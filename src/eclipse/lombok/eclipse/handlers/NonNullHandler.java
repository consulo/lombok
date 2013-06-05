/*
 * Copyright (C) 2013 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.eclipse.handlers;

import static lombok.eclipse.Eclipse.isPrimitive;
import static lombok.eclipse.handlers.EclipseHandlerUtil.generateNullCheck;
import static lombok.eclipse.handlers.EclipseHandlerUtil.isGenerated;

import java.util.Arrays;

import org.eclipse.jdt.internal.compiler.ast.*;
import org.mangosdk.spi.ProviderFor;
import lombok.NonNull;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.eclipse.DeferUntilPostDiet;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;

@DeferUntilPostDiet
@ProviderFor(EclipseAnnotationHandler.class)
public class NonNullHandler extends EclipseAnnotationHandler<NonNull> {
	@Override public void handle(AnnotationValues<NonNull> annotation, Annotation ast, EclipseNode annotationNode) {
		if (annotationNode.up().getKind() == Kind.FIELD) {
			// This is meaningless unless the field is used to generate a method (@Setter, @RequiredArgsConstructor, etc),
			// but in that case those handlers will take care of it. However, we DO check if the annotation is applied to
			// a primitive, because those handlers trigger on any annotation named @NonNull and we only want the warning
			// behaviour on _OUR_ 'lombok.NonNull'.
			
			try {
				if (isPrimitive(((AbstractVariableDeclaration) annotationNode.up().get()).type)) {
					annotationNode.addWarning("@NonNull is meaningless on a primitive.");
				}
			} catch (Exception ignore) {}
			
			return;
		}
		
		if (annotationNode.up().getKind() != Kind.ARGUMENT) return;
		
		Argument arg;
		AbstractMethodDeclaration declaration;
		
		try {
			arg = (Argument) annotationNode.up().get();
			declaration = (AbstractMethodDeclaration) annotationNode.up().up().get();
		} catch (Exception e) {
			return;
		}
		
		if (isGenerated(declaration)) return;
		
		// Possibly, if 'declaration instanceof ConstructorDeclaration', fetch declaration.constructorCall, search it for any references to our parameter,
		// and if they exist, create a new method in the class: 'private static <T> T lombok$nullCheck(T expr, String msg) {if (expr == null) throw NPE; return expr;}' and
		// wrap all references to it in the super/this to a call to this method.
		
		Statement nullCheck = generateNullCheck(arg, ast);
		
		if (nullCheck == null) {
			// @NonNull applied to a primitive. Kinda pointless. Let's generate a warning.
			annotationNode.addWarning("@NonNull is meaningless on a primitive.");
			return;
		}
		
		if (declaration.statements == null) {
			declaration.statements = new Statement[] {nullCheck};
		} else {
			char[] expectedName = arg.name;
			for (Statement stat : declaration.statements) {
				char[] varNameOfNullCheck = returnVarNameIfNullCheck(stat);
				if (varNameOfNullCheck == null) break;
				if (Arrays.equals(expectedName, varNameOfNullCheck)) return;
			}
			
			Statement[] newStatements = new Statement[declaration.statements.length + 1];
			int skipOver = 0;
			for (Statement stat : declaration.statements) {
				if (isGenerated(stat)) skipOver++;
				else break;
			}
			System.arraycopy(declaration.statements, 0, newStatements, 0, skipOver);
			System.arraycopy(declaration.statements, skipOver, newStatements, skipOver + 1, declaration.statements.length - skipOver);
			newStatements[skipOver] = nullCheck;
			declaration.statements = newStatements;
		}
		annotationNode.up().up().rebuild();
	}
	
	private char[] returnVarNameIfNullCheck(Statement stat) {
		if (!(stat instanceof IfStatement)) return null;
		
		/* Check that the if's statement is a throw statement, possibly in a block. */ {
			Statement then = ((IfStatement) stat).thenStatement;
			if (then instanceof Block) {
				Statement[] blockStatements = ((Block) then).statements;
				if (blockStatements == null || blockStatements.length == 0) return null;
				then = blockStatements[0];
			}
			
			if (!(then instanceof ThrowStatement)) return null;
		}
		
		/* Check that the if's conditional is like 'x == null'. Return from this method (don't generate
		   a nullcheck) if 'x' is equal to our own variable's name: There's already a nullcheck here. */ {
			Expression cond = ((IfStatement) stat).condition;
			if (!(cond instanceof EqualExpression)) return null;
			EqualExpression bin = (EqualExpression) cond;
			int operatorId = ((bin.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT);
			if (operatorId != OperatorIds.EQUAL_EQUAL) return null;
			if (!(bin.left instanceof SingleNameReference)) return null;
			if (!(bin.right instanceof NullLiteral)) return null;
			return ((SingleNameReference) bin.left).token;
		}
	}
}
