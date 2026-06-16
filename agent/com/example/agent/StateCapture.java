package com.example.agent;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;

import java.util.List;
import java.util.Map;

/**
 * Core "state extraction" logic: given the thread that hit a
 * breakpoint, walk every frame on its call stack (the breakpoint
 * frame plus every caller above it) and read the visible local
 * variables in each one.
 */
public final class StateCapture {

    private StateCapture() {
    }

    public static Map<String, Object> captureCallStack(ThreadReference thread)
            throws IncompatibleThreadStateException {

        Map<String, Object> result = JsonWriter.orderedMap();
        List<StackFrame> frames = thread.frames();

        java.util.List<Object> frameList = new java.util.ArrayList<>();
        for (int depth = 0; depth < frames.size(); depth++) {
            StackFrame frame = frames.get(depth);
            frameList.add(captureFrame(depth, frame));
        }
        result.put("threadName", thread.name());
        result.put("frames", frameList);
        return result;
    }

    private static Map<String, Object> captureFrame(int depth, StackFrame frame) {
        Map<String, Object> frameInfo = JsonWriter.orderedMap();
        try {
            frameInfo.put("depth", depth);
            frameInfo.put("class", frame.location().declaringType().name());
            frameInfo.put("method", frame.location().method().name());
            frameInfo.put("line", frame.location().lineNumber());

            Map<String, Object> locals = JsonWriter.orderedMap();
            try {
                List<LocalVariable> visible = frame.visibleVariables();
                for (LocalVariable lv : visible) {
                    Value v = frame.getValue(lv);
                    locals.put(lv.name(), v == null ? "null" : v.toString());
                }
            } catch (AbsentInformationException e) {
                locals.put("_error", "no debug info (compile with -g)");
            }
            frameInfo.put("locals", locals);

            // Also capture "this" fields when available (instance method context)
            try {
                if (frame.thisObject() != null) {
                    Map<String, Object> fields = JsonWriter.orderedMap();
                    frame.thisObject().referenceType().fields().forEach(f -> {
                        try {
                            Value v = frame.thisObject().getValue(f);
                            fields.put(f.name(), v == null ? "null" : v.toString());
                        } catch (Exception ignored) {
                        }
                    });
                    frameInfo.put("instanceFields", fields);
                }
            } catch (Exception ignored) {
                // static context or unavailable - fine to skip
            }

        } catch (Exception e) {
            frameInfo.put("_error", "location info unavailable");
        }
        return frameInfo;
    }
}
