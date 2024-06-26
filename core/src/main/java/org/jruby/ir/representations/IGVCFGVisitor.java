package org.jruby.ir.representations;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jruby.ir.Tuple;
import org.jruby.ir.instructions.BranchInstr;
import org.jruby.ir.instructions.Instr;
import org.jruby.ir.instructions.JumpInstr;
import org.jruby.ir.instructions.JumpTargetInstr;
import org.jruby.ir.util.IGVInstrListener;

import static org.jruby.ir.util.IGVDumper.sanitize;
import static org.jruby.ir.util.IGVHelper.emptyTag;
import static org.jruby.ir.util.IGVHelper.endTag;
import static org.jruby.ir.util.IGVHelper.property;
import static org.jruby.ir.util.IGVHelper.startTag;

/**
 * Not a double visitor but I did not want both instr and dirgra
 * to have this visitor have an accept added to it. dirgra is
 * external package and instr I can foresee wanting a different
 * visitor altogether not accessed via CFG.
 */
public class IGVCFGVisitor {
    private class Edge {
        final int from;
        final Object to;
        final String name;

        public Edge(int from, Object to, String name) {
            this.from = from;
            this.to = to;
            this.name = name;
        }
    }
    final PrintStream writer;
    final Map<BasicBlock, Integer> indexOffsets = new HashMap();
    final List<Edge> instrEdges = new ArrayList();
    final List<Edge> extraInstrEdges = new ArrayList();
    Instr lastInstr = null; // Last instr from the previous BB.
    final IGVInstrListener listener;

    public IGVCFGVisitor(CFG cfg, PrintStream writer, String name) {
        this.writer = writer;

        listener = (IGVInstrListener) cfg.getScope().getManager().getInstructionsListener();
        CFG(cfg, name);
        listener.reset();
    }

    protected void visitBasicBlocks(CFG cfg) {
        for (BasicBlock basicBlock: cfg.getBasicBlocks()) {
            BasicBlock(basicBlock);
        }
    }

    protected void visitEdges(CFG cfg) {
        for (BasicBlock basicBlock: cfg.getBasicBlocks()) {
            startTag(writer, "block", "name", basicBlock.getLabel());
            startTag(writer, "successors");
            for (BasicBlock destination: cfg.getOutgoingDestinations(basicBlock)) {
                emptyTag(writer, "successor", "name", destination.getLabel());
            }
            endTag(writer, "successors");
            startTag(writer, "nodes");

            for (Instr instr: basicBlock.getInstrs()) {
                emptyTag(writer, "node", "id", System.identityHashCode(instr));
            }

            for (Instr instr: listener.removedList(basicBlock)) {
                emptyTag(writer, "removeNode", "id", System.identityHashCode(instr));
            }

            endTag(writer, "nodes");
            endTag(writer, "block");
        }
    }

    protected void visitInstrs(BasicBlock basicBlock) {
        List<Instr> instrs = basicBlock.getInstrs();
        int size = instrs.size();

        if (size > 0) {
            int lastIPC = Instr(instrs.get(0));

            // Last BB processed needs to hook up to first in next one if not a Jump (fallthrough)
            if (lastInstr != null && !(lastInstr instanceof JumpInstr)) {
                instrEdges.add(new Edge(System.identityHashCode(lastInstr), lastIPC, "fallthrough"));
            }

            for (int i = 1; i < size; i++) {
                int ipc = Instr(instrs.get(i));
                instrEdges.add(new Edge(lastIPC, ipc, "exception"));
                lastIPC = ipc;
            }

            lastInstr = instrs.get(size - 1);
        }
    }

    public void BasicBlock(BasicBlock basicBlock) {
        // We have potentially empty entry and exit BBs
        if (!basicBlock.getInstrs().isEmpty()) {
            indexOffsets.put(basicBlock, System.identityHashCode(basicBlock.getInstrs().get(0)));
        }
        visitInstrs(basicBlock);
    }

    public void CFG(CFG cfg, String name) {
        startTag(writer, "graph");
        startTag(writer, "properties");
        property(writer, "name", sanitize(name));
        endTag(writer, "properties");

        startTag(writer, "nodes");
        visitBasicBlocks(cfg);
        endTag(writer, "nodes");

        startTag(writer, "edges");

        for (Edge edge: instrEdges) {
            emptyTag(writer, "edge", "from", edge.from, "to", edge.to, "label", edge.name, "name", edge.name);
        }

        for (Edge edge: extraInstrEdges) {
            emptyTag(writer, "edge", "from", edge.from,
                    "to", indexOffsets.get(cfg.getBBForLabel((((JumpTargetInstr) edge.to).getJumpTarget()))),
                    "label", edge.name, "name", edge.name);
        }

        for (Tuple<Instr, Instr> edge: listener.getRemovedEdges()) {
            emptyTag(writer, "removedEdge", "from", System.identityHashCode(edge.a), "to", System.identityHashCode(edge.b));
        }

        endTag(writer, "edges");

        startTag(writer, "controlFlow");
        visitEdges(cfg);
        endTag(writer, "controlFlow");

        endTag(writer, "graph");
    }

    public int Instr(Instr instr) {
        int ipc = System.identityHashCode(instr);

        startTag(writer, "node", "id", ipc);
        startTag(writer, "properties");
        property(writer, "label" , ipc);
        property(writer, "name", instr);

        // We have not processed all BBs yet so we cannot resolve ipc locations of the jumps destinations.
        if (instr instanceof BranchInstr) extraInstrEdges.add(new Edge(ipc, instr, "branch"));

        endTag(writer, "properties");
        endTag(writer, "node");

        return ipc;
    }
}
