package com.example.agent;

import com.sun.jdi.ClassType;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InstrumentationAgent {

    private static final String OUTPUT_FILE = System.getenv().getOrDefault(
            "AGENT_OUTPUT_FILE", "/home/KoulSaksham/jdi-poc/out/captured_state.json");

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        String port = args.length > 1 ? args[1] : "5005";

        List<BreakpointSpec> breakpoints = new ArrayList<>();
        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                breakpoints.add(BreakpointSpec.parse(args[i]));
            }
        } else {
            // Default breakpoints if none supplied on the command line.
            breakpoints.add(BreakpointSpec.parse("com.example.target.AdditionEngine#add"));
            breakpoints.add(BreakpointSpec.parse("com.example.target.MathService#calculate"));
        }

        System.out.println("[agent] attaching to target JVM at " + host + ":" + port + " ...");
        new File(OUTPUT_FILE).getParentFile().mkdirs();
        VirtualMachine vm = attachWithRetry(host, port, 15, 2000);
        System.out.println("[agent] attached. Target VM: " + vm.name() + " / " + vm.version());

        EventRequestManager erm = vm.eventRequestManager();

        // For each requested breakpoint, watch for the owning class to load
        // (it may not be loaded yet at attach time), then resolve the method
        // and set the actual breakpoint once the class is prepared.
        for (BreakpointSpec spec : breakpoints) {
            ClassPrepareRequest cpr = erm.createClassPrepareRequest();
            cpr.addClassFilter(spec.className());
            cpr.putProperty("breakpointSpec", spec);
            cpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            cpr.enable();
            System.out.println("[agent] watching for class load: " + spec.className()
                    + " (target method: " + spec.methodName() + ")");
        }

        // Also handle the case where classes are already loaded (e.g. agent
        // attaches after target has already serviced a request).
        for (BreakpointSpec spec : breakpoints) {
            for (ReferenceType type : vm.classesByName(spec.className())) {
                installBreakpoint(erm, type, spec);
            }
        }

        runEventLoop(vm);
    }

    /**
     * Retries the JDWP attach a few times. Useful when the target's
     * container/process hasn't opened its debug port yet (e.g. a
     * docker-compose `depends_on` only waits for container start,
     * not for the JVM inside it to be listening).
     */
    private static VirtualMachine attachWithRetry(String host, String port, int maxAttempts, long delayMs)
            throws InterruptedException {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return VmAttacher.attach(host, port);
            } catch (Exception e) {
                lastError = e;
                System.out.println("[agent] attach attempt " + attempt + "/" + maxAttempts
                        + " failed (" + e.getMessage() + "), retrying in " + delayMs + "ms...");
                Thread.sleep(delayMs);
            }
        }
        throw new RuntimeException("could not attach to target after " + maxAttempts + " attempts", lastError);
    }

    private static void runEventLoop(VirtualMachine vm) throws IOException {
        EventQueue queue = vm.eventQueue();
        boolean connected = true;

        while (connected) {
            try {
                EventSet eventSet = queue.remove();
                for (Event event : eventSet) {

                    if (event instanceof ClassPrepareEvent cpe) {
                        BreakpointSpec spec = (BreakpointSpec) event.request().getProperty("breakpointSpec");
                        EventRequestManager erm = vm.eventRequestManager();
                        installBreakpoint(erm, cpe.referenceType(), spec);

                    } else if (event instanceof BreakpointEvent be) {
                        handleBreakpointHit(be);

                    } else if (event instanceof VMDisconnectEvent || event instanceof VMDeathEvent) {
                        System.out.println("[agent] target VM disconnected/exited.");
                        connected = false;
                    }
                }
                if (connected) {
                    // CRITICAL: resume immediately after processing so the
                    // target's HTTP handler thread keeps running and the
                    // client still gets its response.
                    eventSet.resume();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                connected = false;
            } catch (VMDisconnectedException e) {
                connected = false;
            }
        }
    }

    private static void installBreakpoint(EventRequestManager erm, ReferenceType type, BreakpointSpec spec) {
        try {
            if (!(type instanceof ClassType)) {
                return;
            }
            List<Method> methods = type.methodsByName(spec.methodName());
            if (methods.isEmpty()) {
                System.out.println("[agent] WARNING: method " + spec.methodName()
                        + " not found on " + type.name());
                return;
            }
            for (Method m : methods) {
                if (m.isAbstract() || m.isNative()) {
                    continue;
                }
                Location loc = m.location();
                BreakpointRequest bp = erm.createBreakpointRequest(loc);
                bp.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                bp.enable();
                System.out.println("[agent] breakpoint armed: " + type.name() + "#" + m.name()
                        + " (line " + loc.lineNumber() + ")");
            }
        } catch (Exception e) {
            System.out.println("[agent] failed to install breakpoint for " + spec + ": " + e.getMessage());
        }
    }

    private static void handleBreakpointHit(BreakpointEvent be) {
        try {
            ThreadReference thread = be.thread();
            Location loc = be.location();

            System.out.println();
            System.out.println("[agent] >>> breakpoint hit: "
                    + loc.declaringType().name() + "#" + loc.method().name()
                    + " (line " + loc.lineNumber() + ") at " + Instant.now());

            Map<String, Object> capture = StateCapture.captureCallStack(thread);
            capture.put("breakpointClass", loc.declaringType().name());
            capture.put("breakpointMethod", loc.method().name());
            capture.put("timestamp", Instant.now().toString());

            String json = JsonWriter.write(capture);

            // Pretty-print summary to stdout
            printSummary(capture);

            // Persist full JSON to file
            try (FileWriter fw = new FileWriter(OUTPUT_FILE, true)) {
                fw.write(json);
                fw.write(System.lineSeparator());
            } catch (IOException ioe) {
                System.out.println("[agent] could not write output file (continuing anyway): " + ioe.getMessage());
            }

        } catch (Exception e) {
            System.out.println("[agent] error capturing state: " + e);
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static void printSummary(Map<String, Object> capture) {
        List<Object> frames = (List<Object>) capture.get("frames");
        for (Object f : frames) {
            Map<String, Object> frame = (Map<String, Object>) f;
            System.out.println("    [" + frame.get("depth") + "] "
                    + frame.get("class") + "#" + frame.get("method")
                    + " (line " + frame.get("line") + ")");
            Map<String, Object> locals = (Map<String, Object>) frame.get("locals");
            for (Map.Entry<String, Object> e : locals.entrySet()) {
                System.out.println("          " + e.getKey() + " = " + e.getValue());
            }
        }
    }
}
