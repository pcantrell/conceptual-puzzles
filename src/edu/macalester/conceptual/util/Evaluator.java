package edu.macalester.conceptual.util;

import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import org.joor.Reflect;

/**
 * A utility to dynamically compile and evaluate Java code, or extract its static types.
 */
public record Evaluator<T> (
    String imports,
    String members,
    String mainCode,
    Class<T> returnType,
    String otherClasses
) {
    private String generateCode() {
        return String.format(
            """
            %1$s

            public class DynamicCode implements java.util.function.Supplier<%4$s> {
                %2$s
                public %4$s get() {
                    %3$s
                }
            }
            
            %5$s
            """,
            imports,
            members,
            mainCode,
            returnType.getName(),
            otherClasses.replaceAll("public\\s+(class|interface|enum|record)", "$1")
        );
    }

    public T evaluate() {
        try {
            Supplier<T> evaluator = Reflect.compile("DynamicCode", generateCode())
                .create().get();
            return evaluator.get();
        } catch(RuntimeException e) {
            throw new EvaluationException(e, this);
        }
    }

    public String captureOutput() {
        return new Evaluator<>(
            imports +
                """
                import java.io.PrintWriter;
                import java.io.StringWriter;
                """,
            members +
                """
                private static StringWriter capturedOutput = new StringWriter();
                public static PrintWriter out = new PrintWriter(capturedOutput);
                """,
            mainCode.replace("System.out", "DynamicCode.out") +
                "return capturedOutput.toString();",
            String.class,
            otherClasses.replace("System.out", "DynamicCode.out")
        ).evaluate();
    }

    public List<?> analyzeStaticTypes() {
        var code = new Evaluator<>(
            imports,
            members +
                """
                private <T> staticType(T val) {  // We'll search for calls to this method after parsing
                    return val;
                }
                """,
            mainCode,
            Void.class,
            otherClasses
        ).generateCode();

        var parserConfig = new ParserConfiguration();
        parserConfig.setSymbolResolver(
            new JavaSymbolSolver(
                new ReflectionTypeSolver()));

        var parseResult = new JavaParser(parserConfig).parse(code);
        if (!parseResult.isSuccessful()) {
            throw new EvaluationException(
                new ParseProblemException(parseResult.getProblems()), this);
        }

        return parseResult
            .getResult().orElseThrow()
            .findAll(
                MethodCallExpr.class,
                methodCall -> methodCall.getName().asString().equals("staticType")
            )
            .stream()
            .map(call -> call.getArgument(0).calculateResolvedType())
            .toList();
    }

    public static class EvaluationException extends RuntimeException {
        private final String javaSource;

        public <T> EvaluationException(Exception cause, Evaluator<T> snippet) {
            super(cause);
            this.javaSource = snippet.generateCode();
        }

        public EvaluationException(Exception cause) {
            super(cause);
            this.javaSource = null;
        }

        public EvaluationException(String message) {
            super(message);
            this.javaSource = null;
        }

        @Override
        public void printStackTrace(PrintStream s) {
            s.println("Error while evaluating expression. Expression wrapper:");
            s.println();
            s.println(javaSource);
            super.printStackTrace(s);
        }
    }
}
