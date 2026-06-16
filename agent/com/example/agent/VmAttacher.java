package com.example.agent;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Handles attaching to a running target JVM over the JDWP socket
 * transport.
 */
public class VmAttacher {

    public static VirtualMachine attach(String host, String port) throws IOException, IllegalConnectorArgumentsException {
        AttachingConnector connector = findSocketAttachConnector();
        Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("hostname").setValue(host);
        args.get("port").setValue(port);
        return connector.attach(args);
    }

    private static AttachingConnector findSocketAttachConnector() {
        List<AttachingConnector> connectors =
                com.sun.jdi.Bootstrap.virtualMachineManager().attachingConnectors();
        for (AttachingConnector c : connectors) {
            if (c.name().equals("com.sun.jdi.SocketAttach")) {
                return c;
            }
        }
        throw new IllegalStateException("No socket attach connector available on this JDK");
    }
}
