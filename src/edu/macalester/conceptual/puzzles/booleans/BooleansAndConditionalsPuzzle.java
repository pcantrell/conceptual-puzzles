package edu.macalester.conceptual.puzzles.booleans;

import edu.macalester.conceptual.Puzzle;
import edu.macalester.conceptual.context.PuzzleContext;

public class BooleansAndConditionalsPuzzle implements Puzzle {
    @Override
    public byte id() {
        return 1;
    }

    @Override
    public String name() {
        return "bool";
    }

    @Override
    public String description() {
        return "Booleans and conditionals";
    }

    public void generate(PuzzleContext ctx) {
        ctx.section(() -> SwapConditionalClausesPuzzle.generate(ctx));

        ctx.section(() -> ReturnBooleanPuzzle.generate(ctx));
    }
}
