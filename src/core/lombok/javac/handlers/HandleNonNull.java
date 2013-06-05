/*
 * Copyright (C) 2013 The Project Lombok Authors.
 * Copyright (C) 2013 Consulo.org
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
package lombok.javac.handlers;

import static lombok.javac.Javac.CTC_BOT;
import static lombok.javac.Javac.CTC_EQUAL;
import static lombok.javac.Javac.getTag;
import static lombok.javac.Javac.isPrimitive;
import static lombok.javac.handlers.JavacHandlerUtil.generateNullCheck;
import static lombok.javac.handlers.JavacHandlerUtil.recursiveSetGeneratedBy;

import org.mangosdk.spi.ProviderFor;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import lombok.NonNull;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleNonNull extends JavacAnnotationHandler<NonNull>
{
	@Override
	public void handle(AnnotationValues<NonNull> annotation, JCAnnotation ast, JavacNode annotationNode)
	{
		final JavacNode up = annotationNode.up();
		final Kind kind = up.getKind();
		if(kind == Kind.FIELD)
		{
			// This is meaningless unless the field is used to generate a method (@Setter, @RequiredArgsConstructor, etc),
			// but in that case those handlers will take care of it. However, we DO check if the annotation is applied to
			// a primitive, because those handlers trigger on any annotation named @NonNull and we only want the warning
			// behaviour on _OUR_ 'lombok.NonNull'.

			try
			{
				if(isPrimitive(((JCVariableDecl) annotationNode.up().get()).vartype))
				{
					annotationNode.addWarning("@NonNull is meaningless on a primitive.");
				}
			}
			catch(Exception ignore)
			{
			}
		}
		else if(kind == Kind.ARGUMENT)
		{
			JCMethodDecl declaration;

			try
			{
				declaration = (JCMethodDecl) annotationNode.up().up().get();
			}
			catch(Exception e)
			{
				return;
			}

			if(JavacHandlerUtil.isGenerated(declaration))
			{
				return;
			}

			// Possibly, if 'declaration instanceof ConstructorDeclaration', fetch declaration.constructorCall, search it for any references to our parameter,
			// and if they exist, create a new method in the class: 'private static <T> T lombok$nullCheck(T expr, String msg) {if (expr == null) throw NPE; return expr;}' and
			// wrap all references to it in the super/this to a call to this method.

			JCStatement nullCheck = recursiveSetGeneratedBy(generateNullCheck(annotationNode.getTreeMaker(), annotationNode.up()), ast);

			if(nullCheck == null)
			{
				// @NonNull applied to a primitive. Kinda pointless. Let's generate a warning.
				annotationNode.addWarning("@NonNull is meaningless on a primitive.");
				return;
			}

			List<JCStatement> statements = declaration.body.stats;

			String expectedName = annotationNode.up().getName();
			for(JCStatement stat : statements)
			{
				if(JavacHandlerUtil.isConstructorCall(stat))
					continue;
				String varNameOfNullCheck = returnVarNameIfNullCheck(stat);
				if(varNameOfNullCheck == null)
					break;
				if(varNameOfNullCheck.equals(expectedName))
					return;
			}

			List<JCStatement> tail = statements;
			List<JCStatement> head = List.nil();
			for(JCStatement stat : statements)
			{
				if(JavacHandlerUtil.isConstructorCall(stat) || JavacHandlerUtil.isGenerated(stat))
				{
					tail = tail.tail;
					head = head.prepend(stat);
					continue;
				}
				break;
			}

			List<JCStatement> newList = tail.prepend(nullCheck);
			for(JCStatement stat : head)
				newList = newList.prepend(stat);
			declaration.body.stats = newList;
		}
		else if(kind == Kind.METHOD)
		{
			final JCMethodDecl tree = (JCMethodDecl) up.get();
			System.out.println(tree.restype);
			if(Javac.isPrimitive(tree.restype))
			{
				annotationNode.addError("@NonNull cant be placed at method with primitive result type.");
				return;
			}

			processBlock(tree.body, up);
		}
	}

	private void processBlock(JCBlock block, final JavacNode methodNode)
	{
		if(block == null)
		{
			return;
		}
		block.accept(new TreeScanner()
		{
			@Override
			public void visitReturn(JCReturn jcReturn)
			{
				changeExpression(jcReturn, methodNode);
			}
		});
	}

	private static void changeExpression(JCReturn oldReturn, JavacNode methodNode)
	{
		if(oldReturn.expr == null)
		{
			return;
		}
		final JCExpression jcExpression = JavacHandlerUtil.chainDotsString(methodNode, "lombok.runtime.NonNullException.throwIfNull");

		final TreeMaker treeMaker = methodNode.getTreeMaker();

		oldReturn.expr = treeMaker.Apply(List.<JCExpression>nil(), jcExpression, List.of(oldReturn.expr));
	}

	/**
	 * Checks if the statement is of the form 'if (x == null) {throw WHATEVER;},
	 * where the block braces are optional. If it is of this form, returns "x".
	 * If it is not of this form, returns null.
	 */
	private String returnVarNameIfNullCheck(JCStatement stat)
	{
		if(!(stat instanceof JCIf))
			return null;
		
		/* Check that the if's statement is a throw statement, possibly in a block. */
		{
			JCStatement then = ((JCIf) stat).thenpart;
			if(then instanceof JCBlock)
			{
				List<JCStatement> stats = ((JCBlock) then).stats;
				if(stats.length() == 0)
					return null;
				then = stats.get(0);
			}
			if(!(then instanceof JCThrow))
				return null;
		}
		
		/* Check that the if's conditional is like 'x == null'. Return from this method (don't generate
		   a nullcheck) if 'x' is equal to our own variable's name: There's already a nullcheck here. */
		{
			JCExpression cond = ((JCIf) stat).cond;
			while(cond instanceof JCParens)
				cond = ((JCParens) cond).expr;
			if(!(cond instanceof JCBinary))
				return null;
			JCBinary bin = (JCBinary) cond;
			if(getTag(bin) != CTC_EQUAL)
				return null;
			if(!(bin.lhs instanceof JCIdent))
				return null;
			if(!(bin.rhs instanceof JCLiteral))
				return null;
			if(((JCLiteral) bin.rhs).typetag != CTC_BOT)
				return null;
			return ((JCIdent) bin.lhs).name.toString();
		}
	}
}
