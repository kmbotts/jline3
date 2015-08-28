/*
 * Copyright (c) 2002-2015, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package org.jline.console;

import java.io.EOFException;
import java.io.IOError;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.fusesource.jansi.Pty;
import org.fusesource.jansi.Pty.Attributes;
import org.jline.Console;
import org.jline.reader.ConsoleReader;
import org.jline.reader.UserInterruptException;
import org.jline.utils.Curses;
import org.jline.utils.InfoCmp;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.Log;

import static org.jline.utils.Preconditions.checkNotNull;

public abstract class AbstractConsole implements Console {

    private final String type;
    private final String appName;
    private final URL inputrc;
    private final Map<String, String> variables;
    private final Map<Signal, SignalHandler> handlers = new HashMap<>();
    private Set<Capability> bools = new HashSet<>();
    private Map<Capability, Integer> ints = new HashMap<>();
    private Map<Capability, String> strings = new HashMap<>();
    private ConsoleReader consoleReader;

    public AbstractConsole(String type, String appName, URL inputrc, Map<String, String> variables) throws IOException {
        this.type = type;
        for (Signal signal : Signal.values()) {
            handlers.put(signal, SignalHandler.SIG_DFL);
        }
        this.appName = appName;
        this.inputrc = inputrc;
        this.variables = variables;
    }

    public ConsoleReader getConsoleReader() {
        synchronized (this) {
            if (consoleReader == null) {
                consoleReader = new ConsoleReader(this, appName, inputrc, variables);
            }
            return consoleReader;
        }
    }

    public SignalHandler handle(Signal signal, SignalHandler handler) {
        checkNotNull(signal);
        checkNotNull(handler);
        return handlers.put(signal, handler);
    }

    public void raise(Signal signal) {
        checkNotNull(signal);
        SignalHandler handler = handlers.get(signal);
        if (handler == SignalHandler.SIG_DFL) {
            handleDefaultSignal(signal);
        } else if (handler != SignalHandler.SIG_IGN) {
            handler.handle(signal);
        }
    }

    protected void handleDefaultSignal(Signal signal) {
    }

    protected void echoSignal(Signal signal) {
        int cc = -1;
        switch (signal) {
            case INT:
                cc = Pty.VINTR;
                break;
            case QUIT:
                cc = Pty.VQUIT;
                break;
            case TSTP:
                cc = Pty.VSUSP;
                break;
        }
        if (cc >= 0) {
            int vcc = getAttributes().getControlChar(cc);
            if (vcc > 0 && vcc < 32) {
                writer().write(new char[]{'^', (char) (vcc + '@')}, 0, 2);
            }
        }
    }

    public Attributes enterRawMode() {
        Attributes prvAttr = getAttributes();
        Attributes newAttr = new Attributes();
        newAttr.copy(prvAttr);
        newAttr.setLocalFlag(Pty.ICANON | Pty.ECHO, false);
        newAttr.setInputFlag(Pty.IXON | Pty.ICRNL | Pty.INLCR, false);
        newAttr.setControlChar(Pty.VMIN, 1);
        newAttr.setControlChar(Pty.VTIME, 0);
        setAttributes(newAttr);
        return prvAttr;
    }

    public boolean echo() {
        return getAttributes().getLocalFlag(Pty.ECHO);
    }

    public boolean echo(boolean echo) {
        Attributes attr = getAttributes();
        boolean prev = attr.getLocalFlag(Pty.ECHO);
        if (prev != echo) {
            attr.setLocalFlag(Pty.ECHO, echo);
            setAttributes(attr);
        }
        return prev;
    }

    public String getType() {
        return type;
    }

    public void flush() throws IOException {
        writer().flush();
    }

    public boolean puts(Capability capability, Object... params) {
        String str = getStringCapability(capability);
        if (str == null) {
            return false;
        }
        try {
            Curses.tputs(writer(), str, params);
        } catch (IOException e) {
            throw new IOError(e);
        }
        return true;
    }

    public boolean getBooleanCapability(Capability capability) {
        return bools.contains(capability);
    }

    public Integer getNumericCapability(Capability capability) {
        return ints.get(capability);
    }

    public String getStringCapability(Capability capability) {
        return strings.get(capability);
    }

    protected void parseInfoCmp() {
        String capabilities = null;
        if (type != null) {
            try {
                capabilities = InfoCmp.getInfoCmp(type);
            } catch (Exception e) {
                Log.warn("Unable to retrieve infocmp for type " + type, e);
            }
        }
        if (capabilities == null) {
            capabilities = InfoCmp.ANSI_CAPS;
        }
        InfoCmp.parseInfoCmp(capabilities, bools, ints, strings);
    }

    @Override
    public String readLine() throws UserInterruptException, EOFException {
        return getConsoleReader().readLine();
    }

    @Override
    public String readLine(Character mask) throws UserInterruptException, EOFException {
        return getConsoleReader().readLine(mask);
    }

    @Override
    public String readLine(String prompt) throws UserInterruptException, EOFException {
        return getConsoleReader().readLine(prompt);
    }

    @Override
    public String readLine(String prompt, Character mask) throws UserInterruptException, EOFException {
        return getConsoleReader().readLine(prompt, mask);
    }

    @Override
    public String readLine(String prompt, Character mask, String buffer) throws UserInterruptException, EOFException {
        return getConsoleReader().readLine(prompt, mask, buffer);
    }
}