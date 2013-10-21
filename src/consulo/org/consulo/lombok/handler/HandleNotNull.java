package org.consulo.lombok.handler;

import static lombok.javac.Javac.CTC_BOT;
import static lombok.javac.Javac.CTC_EQUAL;
import static lombok.javac.Javac.getTag;
import static lombok.javac.Javac.isPrimitive;
import static lombok.javac.handlers.JavacHandlerUtil.generateNullCheck;
import static lombok.javac.handlers.JavacHandlerUtil.recursiveSetGeneratedBy;

import org.jetbrains.annotations.NotNull;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.handlers.JavacHandlerUtil;

/**
 * @author VISTALL
 * @since 14:50/08.06.13
 *
 * This is backed for Jetbrains @NotNull annotation. After full refusing  - delete it
 * @see lombok.javac.handlers.HandleNonNull
 */
@Deprecated
//@ProviderFor(JavacAnnotationHandler.class)
public class HandleNotNull extends JavacAnnotationHandler<NotNull>
{
	@Override
	public void handle(AnnotationValues<NotNull> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode)
	{
		final JavacNode up = annotationNode.up();
		final AST.Kind kind = up.getKind();
		if(kind == AST.Kind.FIELD)
		{
			// This is meaningless unless the field is used to generate a method (@Setter, @RequiredArgsConstructor, etc),
			// but in that case those handlers will take care of it. However, we DO check if the annotation is applied to
			// a primitive, because those handlers trigger on any annotation named @NonNull and we only want the warning
			// behaviour on _OUR_ 'lombok.NonNull'.

			try
			{
				if(isPrimitive(((JCTree.JCVariableDecl) annotationNode.up().get()).vartype))
				{
					annotationNode.addWarning("@NotNull is meaningless on a primitive.");
				}
			}
			catch(Exception ignore)
			{
			}
		}
		else if(kind == AST.Kind.ARGUMENT)
		{
			JCTree.JCMethodDecl declaration;

			try
			{
				declaration = (JCTree.JCMethodDecl) annotationNode.up().up().get();
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

			JCTree.JCStatement nullCheck = recursiveSetGeneratedBy(generateNullCheck(annotationNode.getTreeMaker(), annotationNode.up()), ast);

			if(nullCheck == null)
			{
				// @NonNull applied to a primitive. Kinda pointless. Let's generate a warning.
				annotationNode.addWarning("@NotNull is meaningless on a primitive.");
				return;
			}

			if(declaration.body == null)
			{
				return;
			}

			List<JCTree.JCStatement> statements = declaration.body.stats;

			String expectedName = annotationNode.up().getName();
			for(JCTree.JCStatement stat : statements)
			{
				if(JavacHandlerUtil.isConstructorCall(stat))
					continue;
				String varNameOfNullCheck = returnVarNameIfNullCheck(stat);
				if(varNameOfNullCheck == null)
					break;
				if(varNameOfNullCheck.equals(expectedName))
					return;
			}

			List<JCTree.JCStatement> tail = statements;
			List<JCTree.JCStatement> head = List.nil();
			for(JCTree.JCStatement stat : statements)
			{
				if(JavacHandlerUtil.isConstructorCall(stat) || JavacHandlerUtil.isGenerated(stat))
				{
					tail = tail.tail;
					head = head.prepend(stat);
					continue;
				}
				break;
			}

			List<JCTree.JCStatement> newList = tail.prepend(nullCheck);
			for(JCTree.JCStatement stat : head)
				newList = newList.prepend(stat);
			declaration.body.stats = newList;
		}
		else if(kind == AST.Kind.METHOD)
		{
			final JCTree.JCMethodDecl tree = (JCTree.JCMethodDecl) up.get();

			if(Javac.isPrimitive(tree.restype))
			{
				annotationNode.addError("@NotNull cant be placed at method with primitive result type.");
				return;
			}

			processBlock(tree.body, up);
		}
	}

	private void processBlock(JCTree.JCBlock block, final JavacNode methodNode)
	{
		if(block == null)
		{
			return;
		}
		block.accept(new TreeScanner()
		{
			@Override
			public void visitReturn(JCTree.JCReturn jcReturn)
			{
				changeExpression(jcReturn, methodNode);
			}
		});
	}

	private static void changeExpression(JCTree.JCReturn oldReturn, JavacNode methodNode)
	{
		if(oldReturn.expr == null)
		{
			return;
		}
		final JCTree.JCExpression jcExpression = JavacHandlerUtil.chainDotsString(methodNode, "lombok.runtime.NonNullException.throwIfNull");

		final TreeMaker treeMaker = methodNode.getTreeMaker();

		oldReturn.expr = treeMaker.Apply(List.<JCTree.JCExpression>nil(), jcExpression, List.of(oldReturn.expr));
	}

	/**
	 * Checks if the statement is of the form 'if (x == null) {throw WHATEVER;},
	 * where the block braces are optional. If it is of this form, returns "x".
	 * If it is not of this form, returns null.
	 */
	private String returnVarNameIfNullCheck(JCTree.JCStatement stat)
	{
		if(!(stat instanceof JCTree.JCIf))
			return null;

		/* Check that the if's statement is a throw statement, possibly in a block. */
		{
			JCTree.JCStatement then = ((JCTree.JCIf) stat).thenpart;
			if(then instanceof JCTree.JCBlock)
			{
				List<JCTree.JCStatement> stats = ((JCTree.JCBlock) then).stats;
				if(stats.length() == 0)
					return null;
				then = stats.get(0);
			}
			if(!(then instanceof JCTree.JCThrow))
				return null;
		}

		/* Check that the if's conditional is like 'x == null'. Return from this method (don't generate
		   a nullcheck) if 'x' is equal to our own variable's name: There's already a nullcheck here. */
		{
			JCTree.JCExpression cond = ((JCTree.JCIf) stat).cond;
			while(cond instanceof JCTree.JCParens)
				cond = ((JCTree.JCParens) cond).expr;
			if(!(cond instanceof JCTree.JCBinary))
				return null;
			JCTree.JCBinary bin = (JCTree.JCBinary) cond;
			if(getTag(bin) != CTC_EQUAL)
				return null;
			if(!(bin.lhs instanceof JCTree.JCIdent))
				return null;
			if(!(bin.rhs instanceof JCTree.JCLiteral))
				return null;
			if(((JCTree.JCLiteral) bin.rhs).typetag != CTC_BOT)
				return null;
			return ((JCTree.JCIdent) bin.lhs).name.toString();
		}
	}
}

