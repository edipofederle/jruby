package org.jruby.ast.java_signature;

/*
 * A || B
 * {A, B}
 * "value"
 * @Anno
 */
public interface AnnotationExpression {
	public <T> T accept(AnnotationVisitor<T> visitor);
}
