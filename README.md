# Dynamic Instrumentation PoC

Small Java project with two parts: a plain HTTP calculator (the target) and a
separate agent that attaches at runtime and reads local variables off the call
stack when specific methods are hit. The target has no logging, tracing, or
instrumentation code anywhere in its source.

Tested on OpenJDK 21. The agent needs a full JDK (for `com.sun.jdi`); the
target only needs a JRE at runtime.

## Run it (Docker)

You need Docker with Compose. No local Java install required.

1. From the repo root, build and start both containers:

   ```bash
   docker compose up --build
   ```

   This brings up two services on a shared network:
   - `target` — the calculator on port 8080, JDWP debug port on 5005
   - `agent` — attaches to `target:5005`, arms breakpoints, prints captures

   The agent container bind-mounts `./out` so captured JSON lands on your
   host at `out/captured_state.json`.

2. In another terminal, hit the endpoint:

   ```bash
   curl "http://localhost:8080/calculate?op=add&a=10&b=20"
   ```

   You should get back something like
   `{"op":"add","a":10.0,"b":20.0,"result":30.0}`.

3. Check the `docker compose` logs for the agent service. On each request
   you'll see breakpoint hits walking down the call chain
   (`RouteHandler#handle` → `MathService#calculate` → `AdditionEngine#add`),
   with locals printed per frame. The same data is appended as JSON lines in
   `out/captured_state.json`.

To tear down: `Ctrl+C`, then `docker compose down` if you want to remove the
containers.

### Running the images without Compose

There are two Dockerfiles — `Dockerfile.target` and `Dockerfile.agent` — if
you prefer to run them manually:

```bash
docker build -f Dockerfile.target -t jdi-target .
docker build -f Dockerfile.agent  -t jdi-agent  .

docker network create jdi-net
docker run --rm --name jdi-poc-target --network jdi-net -p 8080:8080 -p 5005:5005 jdi-target
docker run --rm --name jdi-poc-agent --network jdi-net \
  -v "$(pwd)/out:/app/out" -e TARGET_HOST=jdi-poc-target jdi-agent
```

## Architecture

The target is a minimal `HttpServer` app. A request to `/calculate` flows
through `RouteHandler.handle` → `MathService.calculate` →
`AdditionEngine.add` (or `MultiplicationEngine.multiply` for multiply/divide).
That's four frames of application code before the result comes back. Nothing in
that path knows an agent exists.

The agent is a separate JVM process that connects to the target over JDWP
(Java Debug Wire Protocol) using the JDK's JDI API (`com.sun.jdi`). At startup
it takes breakpoint specs in the form `com.example.target.Foo#bar`, watches for
those classes to load, and sets method-entry breakpoints. When one fires, the
agent suspends the target thread, walks every frame on that thread's stack, and
reads the visible local variables (and `this` fields where available) in the
breakpoint frame and all callers above it. It writes the capture to stdout and
appends a JSON line to `out/captured_state.json`, then resumes the target
immediately. The HTTP response is not blocked waiting on the agent.

The target's only "cooperation" is operational, not a code change: it must be
started with `-agentlib:jdwp=...` so the debug socket is open, and compiled
with `-g` so the class files carry a local variable table. Without `-g`, JDI can
still see the stack but not variable names. These are standard debug build
settings — the application logic itself is untouched.

## Limitations

This is a PoC, not something I'd run against a busy production service as-is.

- **JDWP must be enabled on the target JVM.** That's how the agent gets in.
  It's a launch-time flag, not injected source code, but it does mean the
  process is debuggable — which has security and overhead implications in
  production.

- **Breakpoints suspend the whole VM briefly.** The agent resumes right away,
  but under load those pauses would add up. A real version would need sampling,
  async export, or a lighter-weight probe mechanism.

- **Method-entry breakpoints only.** Specs are `ClassName#methodName`. No line
  numbers or overload disambiguation yet. Adding line breakpoints is
  straightforward in JDI (`locationsOfLine`) but not implemented here.

- **Values are shallow.** Locals come back as JDI `toString()` output — fine
  for primitives and simple strings, not great for deep object graphs. The
  hand-rolled `JsonWriter` would need replacing (Gson/Jackson) for anything
  richer.

- **Requires debug symbols.** Target classes must be compiled with `-g`. Most
  dev/CI builds already do this, but a stripped production build would give
  stack frames without usable local names.

- **Agent needs a JDK.** JDI lives in `jdk.jdi`, which isn't on the JRE
  classpath. The target container uses a slim JRE image; the agent uses the
  full JDK.