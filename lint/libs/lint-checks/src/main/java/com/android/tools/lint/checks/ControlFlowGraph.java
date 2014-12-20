/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Maps;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@linkplain ControlFlowGraph} is a graph containing a node for each
 * instruction in a method, and an edge for each possible control flow; usually
 * just "next" for the instruction following the current instruction, but in the
 * case of a branch such as an "if", multiple edges to each successive location,
 * or with a "goto", a single edge to the jumped-to instruction.
 * <p>
 * It also adds edges for abnormal control flow, such as the possibility of a
 * method call throwing a runtime exception.
 */
public class ControlFlowGraph {
    /** Map from instructions to nodes */
    private Map<AbstractInsnNode, Node> mNodeMap;

    /**
     * Creates a new {@link ControlFlowGraph} and populates it with the flow
     * control for the given method. If the optional {@code initial} parameter is
     * provided with an existing graph, then the graph is simply populated, not
     * created. This allows subclassing of the graph instance, if necessary.
     *
     * @param initial usually null, but can point to an existing instance of a
     *            {@link ControlFlowGraph} in which that graph is reused (but
     *            populated with new edges)
     * @param classNode the class containing the method to be analyzed
     * @param method the method to be analyzed
     * @return a {@link ControlFlowGraph} with nodes for the control flow in the
     *         given method
     * @throws AnalyzerException if the underlying bytecode library is unable to
     *             analyze the method bytecode
     */
    @NonNull
    public static ControlFlowGraph create(
            @Nullable ControlFlowGraph initial,
            @NonNull ClassNode classNode,
            @NonNull MethodNode method) throws AnalyzerException {
        final ControlFlowGraph graph = initial != null ? initial : new ControlFlowGraph();
        final InsnList instructions = method.instructions;
        graph.mNodeMap = Maps.newHashMapWithExpectedSize(instructions.size());

        // Create a flow control graph using ASM5's analyzer. According to the ASM 4 guide
        // (download.forge.objectweb.org/asm/asm4-guide.pdf) there are faster ways to construct
        // it, but those require a lot more code.
        Analyzer analyzer = new Analyzer(new BasicInterpreter()) {
            @Override
            protected void newControlFlowEdge(int insn, int successor) {
                // Update the information as of whether the this object has been
                // initialized at the given instruction.
                AbstractInsnNode from = instructions.get(insn);
                AbstractInsnNode to = instructions.get(successor);
                graph.add(from, to);
            }

            @Override
            protected boolean newControlFlowExceptionEdge(int insn, TryCatchBlockNode tcb) {
                AbstractInsnNode from = instructions.get(insn);
                graph.exception(from, tcb);
                return super.newControlFlowExceptionEdge(insn, tcb);
            }

            @Override
            protected boolean newControlFlowExceptionEdge(int insn, int successor) {
                AbstractInsnNode from = instructions.get(insn);
                AbstractInsnNode to = instructions.get(successor);
                graph.exception(from, to);
                return super.newControlFlowExceptionEdge(insn, successor);
            }
        };

        analyzer.analyze(classNode.name, method);
        return graph;
    }

    /** A {@link Node} is a node in the control flow graph for a method, pointing to
     * the instruction and its possible successors */
    public static class Node {
        /** The instruction */
        public final AbstractInsnNode instruction;
        /** Any normal successors (e.g. following instruction, or goto or conditional flow) */
        public final List<Node> successors = new ArrayList<Node>(2);
        /** Any abnormal successors (e.g. the handler to go to following an exception) */
        public final List<Node> exceptions = new ArrayList<Node>(1);

        /** A tag for use during depth-first-search iteration of the graph etc */
        public int visit;

        /**
         * Constructs a new control graph node
         *
         * @param instruction the instruction to associate with this node
         */
        public Node(@NonNull AbstractInsnNode instruction) {
            this.instruction = instruction;
        }

        void addSuccessor(@NonNull Node node) {
            if (!successors.contains(node)) {
                successors.add(node);
            }
        }

        void addExceptionPath(@NonNull Node node) {
            if (!exceptions.contains(node)) {
                exceptions.add(node);
            }
        }

        /**
         * Represents this instruction as a string, for debugging purposes
         *
         * @param includeAdjacent whether it should include a display of
         *            adjacent nodes as well
         * @return a string representation
         */
        @NonNull
        public String toString(boolean includeAdjacent) {
            StringBuilder sb = new StringBuilder(100);

            sb.append(getId(instruction));
            sb.append(':');

            if (instruction instanceof LabelNode) {
                //LabelNode l = (LabelNode) instruction;
                //sb.append('L' + l.getLabel().getOffset() + ":");
                //sb.append('L' + l.getLabel().info + ":");
                sb.append("LABEL");
            } else if (instruction instanceof LineNumberNode) {
                sb.append("LINENUMBER ").append(((LineNumberNode)instruction).line);
            } else if (instruction instanceof FrameNode) {
                sb.append("FRAME");
            } else {
                int opcode = instruction.getOpcode();
                // AbstractVisitor isn't available unless debug/util is included,
                boolean printed = false;
                try {
                    Class<?> cls = Class.forName("org.objectweb.asm.util"); //$NON-NLS-1$
                    Field field = cls.getField("OPCODES");
                    String[] OPCODES = (String[]) field.get(null);
                    printed = true;
                    if (opcode > 0 && opcode <= OPCODES.length) {
                        sb.append(OPCODES[opcode]);
                        if (instruction.getType() == AbstractInsnNode.METHOD_INSN) {
                            sb.append('(').append(((MethodInsnNode)instruction).name).append(')');
                        }
                    }
                } catch (Throwable t) {
                    // debug not installed: just do toString() on the instructions
                }
                if (!printed) {
                    if (instruction.getType() == AbstractInsnNode.METHOD_INSN) {
                        sb.append('(').append(((MethodInsnNode)instruction).name).append(')');
                    } else {
                        sb.append(instruction.toString());
                    }
                }
            }

            if (includeAdjacent) {
                if (successors != null && !successors.isEmpty()) {
                    sb.append(" Next:");
                    for (Node successor : successors) {
                        sb.append(' ');
                        sb.append(successor.toString(false));
                    }
                }

                if (exceptions != null && !exceptions.isEmpty()) {
                    sb.append(" Exceptions:");
                    for (Node exception : exceptions) {
                        sb.append(' ');
                        sb.append(exception.toString(false));
                    }
                }
                sb.append('\n');
            }

            return sb.toString();
        }
    }

    /** Adds an exception flow to this graph */
    protected void add(@NonNull AbstractInsnNode from, @NonNull AbstractInsnNode to) {
        getNode(from).addSuccessor(getNode(to));
    }

    /** Adds an exception flow to this graph */
    protected void exception(@NonNull AbstractInsnNode from, @NonNull AbstractInsnNode to) {
        // For now, these edges appear useless; we also get more specific
        // information via the TryCatchBlockNode which we use instead.
        //getNode(from).addExceptionPath(getNode(to));
    }

    /** Adds an exception try block node to this graph */
    protected void exception(@NonNull AbstractInsnNode from, @NonNull TryCatchBlockNode tcb) {
        // Add tcb's to all instructions in the range
        LabelNode start = tcb.start;
        LabelNode end = tcb.end; // exclusive

        // Add exception edges for all method calls in the range
        AbstractInsnNode curr = start;
        Node handlerNode = getNode(tcb.handler);
        while (curr != end && curr != null) {
            if (curr.getType() == AbstractInsnNode.METHOD_INSN) {
                // Method call; add exception edge to handler
                if (tcb.type == null) {
                    // finally block: not an exception path
                    getNode(curr).addSuccessor(handlerNode);
                }
                getNode(curr).addExceptionPath(handlerNode);
            }
            curr = curr.getNext();
        }
    }

    /**
     * Looks up (and if necessary) creates a graph node for the given instruction
     *
     * @param instruction the instruction
     * @return the control flow graph node corresponding to the given
     *         instruction
     */
    @NonNull
    public Node getNode(@NonNull AbstractInsnNode instruction) {
        Node node = mNodeMap.get(instruction);
        if (node == null) {
            node = new Node(instruction);
            mNodeMap.put(instruction, node);
        }

        return node;
    }

    /**
     * Creates a human readable version of the graph
     *
     * @param start the starting instruction, or null if not known or to use the
     *            first instruction
     * @return a string version of the graph
     */
    @NonNull
    public String toString(@Nullable Node start) {
        StringBuilder sb = new StringBuilder(400);

        AbstractInsnNode curr;
        if (start != null) {
            curr = start.instruction;
        } else {
            if (mNodeMap.isEmpty()) {
                return "<empty>";
            } else {
                curr = mNodeMap.keySet().iterator().next();
                while (curr.getPrevious() != null) {
                    curr = curr.getPrevious();
                }
            }
        }

        while (curr != null) {
            Node node = mNodeMap.get(curr);
            if (node != null) {
                sb.append(node.toString(true));
            }
            curr = curr.getNext();
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toString(null);
    }

    // ---- For debugging only ----

    private static Map<Object, String> sIds = null;
    private static int sNextId = 1;
    private static String getId(Object object) {
        if (sIds == null) {
            sIds = Maps.newHashMap();
        }
        String id = sIds.get(object);
        if (id == null) {
            id = Integer.toString(sNextId++);
            sIds.put(object, id);
        }
        return id;
    }
}

