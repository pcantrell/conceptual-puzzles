package edu.macalester.conceptual.puzzles.vars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import edu.macalester.conceptual.Puzzle;
import edu.macalester.conceptual.context.PuzzleContext;
import edu.macalester.conceptual.util.CodeSnippet;
import edu.macalester.conceptual.util.Evaluator;
import edu.macalester.conceptual.util.Nonsense;
import edu.macalester.conceptual.util.PlaceholderGenerator;

import static edu.macalester.conceptual.util.CodeFormatting.*;
import static edu.macalester.conceptual.util.Randomness.*;
import static java.util.stream.Collectors.joining;

public class VariablesPuzzle implements Puzzle {
    // TODO: When we're ready to require Java 21, we can use String templates instead of the
    //       string substitutions in ScopeTestPoint, and make these variables local to `generate`.
    private String
        className, twiddleMethodName,
        parameterName, staticVarName, instanceVarName,
        localVarName, mainLocal0Name, mainLocal1Name;
    private PlaceholderGenerator placeholders = new PlaceholderGenerator();

    @Override
    public byte id() {
        return 3;
    }

    @Override
    public String name() {
        return "vars";
    }

    @Override
    public String description() {
        return "Variable scope and lifetime";
    }

    @Override
    public void generate(PuzzleContext ctx) {
        className = Nonsense.typeName(ctx);
        twiddleMethodName = Nonsense.methodName(ctx);
        parameterName = Nonsense.variableName(ctx);
        staticVarName = Nonsense.variableName(ctx);
        instanceVarName = Nonsense.variableName(ctx);
        localVarName = Nonsense.variableName(ctx);

        var threeVars = shuffledListOf(ctx, staticVarName, instanceVarName, localVarName);

        // Generate `twiddling`, the sequence of method calls and assignments that mess with the two
        // objects we will create in the main method

        var mainLocalVar = className.substring(0, 1).toLowerCase();
        mainLocal0Name = mainLocalVar + "0";
        mainLocal1Name = mainLocalVar + "1";
        var twiddling = generateList(4, (i, j) ->
            mainLocalVar + (i % 2) + "." + twiddleMethodName + "(" + (int) Math.pow(10, i) + ")");
        int n = ctx.getRandom().nextInt(2), m = 1 - n;
        insertAtRandomPosition(ctx,
            twiddling,
            1, -2,
            mainLocalVar + n + "="
                + (ctx.getRandom().nextFloat() < 0.75
                    ? mainLocalVar + m
                    : "new " + className + "()"));
        insertAtRandomPosition(ctx,
            twiddling,
            1, -2,
            mainLocalVar + m + "="
                + (ctx.getRandom().nextFloat() < 0.25
                    ? mainLocalVar + n
                    : "new " + className + "()"));

        var questionVars = new ArrayList<>(threeVars);
        questionVars.add(mainLocal0Name);
        questionVars.add(mainLocal1Name);

        // Test points we’ll use for the scope questions

        var instanceMethodStart = new ScopeTestPoint(
            List.of(staticVarName, instanceVarName),
            """
            `{localVarName}` is out of scope because it is not declared yet.
            `{mainLocal0Name}` and `{mainLocal1Name}` out of scope because they are local to the
            `main` method.
            """
        );
        var instanceMethodMiddle = new ScopeTestPoint(
            List.of(staticVarName, instanceVarName, localVarName),
            """
            `{mainLocal0Name}` and `{mainLocal1Name}` out of scope because they are local to the
            `main` method.
            """
        );
        var instanceMethodEnd = new ScopeTestPoint(
            List.of(staticVarName, instanceVarName, localVarName),
            """
            `{mainLocal0Name}` and `{mainLocal1Name}` out of scope because they are local to the
            `main` method.
            """
        );
        var mainMethodStart = new ScopeTestPoint(
            List.of(staticVarName),
            """
            `{mainLocal0Name}` and `{mainLocal1Name}` are out of scope because they are not
            declared yet.
            `{instanceVarName}` is out of scope because it is an _instance_ variable, but `main`
            is a _static_ method.
            `{localVarName}` is out of scope because it is local to `{twiddleMethodName}`.
            """
        );
        var mainMethodAfter0 = new ScopeTestPoint(
            List.of(staticVarName, mainLocal0Name),
            """
            `{mainLocal1Name}` is out of scope because it is not declared yet.
            `{instanceVarName}` is out of scope because it is an _instance_ variable, but `main`
            is a _static_ method.
            `{localVarName}` is out of scope because it is local to `{twiddleMethodName}`.
            """
        );
        var mainMethodMiddle = new ScopeTestPoint(
            List.of(staticVarName, mainLocal0Name, mainLocal1Name),
            """
            `{instanceVarName}` is out of scope because it is an _instance_ variable, but `main`
            is a _static_ method.
            `{localVarName}` is out of scope because it is local to `{twiddleMethodName}`.
            """
        );
        var mainMethodEnd = new ScopeTestPoint(
            List.of(staticVarName, mainLocal0Name, mainLocal1Name),
            """
            `{instanceVarName}` is out of scope because it is an _instance_ variable, but `main`
            is a _static_ method.
            `{localVarName}` is out of scope because it is local to `{twiddleMethodName}`.
            """
        );

        // Randomly eliminate some of the test points

        choose(ctx, () -> mainMethodStart, () -> mainMethodAfter0).disable();

        choose(ctx, () -> mainMethodMiddle, () -> mainMethodEnd).disable();

        instanceMethodStart.disable();
        instanceMethodMiddle.disable();
        instanceMethodEnd.disable();
        choose(ctx, () -> instanceMethodStart, () -> instanceMethodMiddle, () -> instanceMethodEnd)
            .enable();

        // Generate the class members (variables and methods) in random order.

        List<Supplier<String>> memberGenerators =
            shuffledListOf(ctx,
                () -> "private static int " + staticVarName + " = 0;",
                () -> "private int " + instanceVarName + " = 0;",
                () -> "public void " + twiddleMethodName + "(int " + parameterName + ") {"
                    + instanceMethodStart.makePlaceholder()
                    + "int " + localVarName + " = 0;"
                    // Increment static, instance, and local var, in random order
                    + instanceMethodMiddle.makePlaceholder()
                    + threeVars.stream()
                        .map(v -> v + "+=" + parameterName + ";")
                        .collect(joining())
                    // Print all three in same order we incremented
                    + "System.out.println("
                        + threeVars.stream()
                            .map(v -> "\"" + v + "=\"+" + v)
                            .collect(joining("+"))
                            .replace("+\"", "+\"  ")
                    + ");"
                    + instanceMethodEnd.makePlaceholder()
                    + "}",
                () -> "public static void main(String[] args) {"
                    + mainMethodStart.makePlaceholder()
                    + className + " " + mainLocalVar + "0 = new " + className + "();"
                    + mainMethodAfter0.makePlaceholder()
                    + className + " " + mainLocalVar + "1 = new " + className + "();"
                    + mainMethodMiddle.makePlaceholder()
                    + joinStatements(twiddling)
                    + mainMethodEnd.makePlaceholder()
                    + "}"
            );

        // We shuffle first and _then_ generate so that the placeholder names end up in display
        // order, forcing students to actually read the code instead of memorizing the answer order.
        var members = memberGenerators.stream().map(Supplier::get).toList();

        // Now that we've determined the display order of the placeholders,
        // we can determine the order of questions to ask:
        var enabledScopeTestPoints =
            Stream.of(
                instanceMethodStart, instanceMethodMiddle, instanceMethodEnd, mainMethodStart,
                mainMethodAfter0, mainMethodMiddle, mainMethodEnd
            )
                .filter(ScopeTestPoint::isEnabled)
                .sorted(Comparator.comparing(ScopeTestPoint::makePlaceholder))
                .toList();

        // Put it all together into a full class declaration!

        var classDecl = prettifyTypeDecl(
            "public class " + className + "{"
                + joinCode(members)
            + "}");

        // Instructions

        ctx.output().paragraph("Given the following code:");
        ctx.output().codeBlock(classDecl);
        ctx.output().numberedList(
            Stream.concat(
                Stream.of(
                    "What does the main method print?",
                    "Which of the variables "
                        + questionVars.stream().map(s -> "`" + s + "`").toList()
                        + " are in scope at " + enabledScopeTestPoints.get(0).makePlaceholder()
                        + "?"
                ),
                enabledScopeTestPoints.stream().skip(1).map(scopeTest ->
                    "Which are in scope at " + scopeTest.makePlaceholder() + "?"
                )
            ).toArray(String[]::new)
        );

        ctx.solution(() -> {
            ctx.output().numberedList(
                Stream.concat(
                    Stream.of((Runnable)
                        () -> {
                            ctx.output().paragraph("Output:");
                            ctx.output().codeBlock(
                                Evaluator.captureOutput(
                                    CodeSnippet.build()
                                        .withMainBody(className + ".main(null);")
                                        .withOtherClasses(classDecl)
                                )
                            );
                        }
                    ),
                    enabledScopeTestPoints.stream().map(scopeTest ->
                        () -> {
                            ctx.output().paragraph(
                                "In scope at " + scopeTest.makePlaceholder() + ": "
                                    + scopeTest.getAnswer().stream()
                                        .map(v -> "`" + v + "`")
                                        .collect(joining(", "))
                            );
                        }
                    )
                ).toList());
            ctx.output().dividerLine(false);
            ctx.output().paragraph(
                "Explanation (which you do _not_ need to write out in your submitted solution):");
            ctx.output().numberedList(
                Stream.concat(
                    Stream.of((Runnable)
                        () -> {
                            ctx.output().paragraph(formatVarNames(
                                """
                                `{staticVarName}` is a static variable,
                                `{instanceVarName}` is an instance variable, and
                                `{localVarName}` is a local variable.
                                """
                            ));
                        }
                    ),
                    enabledScopeTestPoints.stream().map(scopeTest ->
                        () -> {
                            ctx.output().paragraph(
                                "At " + scopeTest.makePlaceholder() + ", "
                                    + formatVarNames(scopeTest.getExplanation())
                            );
                        }
                    )
                ).toList());
        });
    }

    // TODO: Remove in Java 21 (see note above)
    private String formatVarNames(String str) {
        return str
            .replace("{className}",         className)
            .replace("{twiddleMethodName}", twiddleMethodName)
            .replace("{parameterName}",     parameterName)
            .replace("{staticVarName}",     staticVarName)
            .replace("{instanceVarName}",   instanceVarName)
            .replace("{localVarName}",      localVarName)
            .replace("{mainLocal0Name}",    mainLocal0Name)
            .replace("{mainLocal1Name}",    mainLocal1Name);
    }

    private class ScopeTestPoint {
        private final List<String> answer;
        private final String explanation;
        private boolean enabled = true;
        private String placeholder;

        public ScopeTestPoint(List<String> answer, String explanation) {
            this.answer = answer;
            this.explanation = explanation;
        }

        public void enable() {
            enabled = true;
        }

        public void disable() {
            enabled = false;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public List<String> getAnswer() {
            return answer;
        }

        public String getExplanation() {
            return explanation;
        }

        public String makePlaceholder() {
            if (!enabled) {
                return "";
            }

            if (placeholder == null) {
                placeholders.next();
                placeholder = "\n/*" + placeholders.current() + "*/\n";
            }

            return placeholder;
        }
    }
}
