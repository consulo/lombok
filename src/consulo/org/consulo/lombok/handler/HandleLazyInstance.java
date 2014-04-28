package org.consulo.lombok.handler;

import static lombok.javac.handlers.JavacHandlerUtil.chainDotsString;
import static lombok.javac.handlers.JavacHandlerUtil.injectField;

import java.lang.reflect.Modifier;

import org.consulo.lombok.annotations.LazyInstance;
import org.jetbrains.annotations.NotNull;
import org.mangosdk.spi.ProviderFor;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.core.HandlerPriority;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;

/**
 * @author VISTALL
 * @since 28.04.14
 */
@HandlerPriority(value = Short.MAX_VALUE)
@ProviderFor(JavacAnnotationHandler.class)
public class HandleLazyInstance extends JavacAnnotationHandler<LazyInstance>
{
	// com.intellij.openapi.util.NotNullLazyValue
	// com.intellij.openapi.util.NullableLazyValue
	@Override
	public void handle(AnnotationValues<LazyInstance> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode)
	{
		boolean notNull = annotation.getInstance().notNull();
		final TreeMaker treeMaker = annotationNode.getTreeMaker();

		final JavacNode up = annotationNode.up();
		final AST.Kind kind = up.getKind();

		JavacNode classNode;
		String fieldName = null;
		JCTree.JCExpression paramType = null;
		JCTree.JCMethodDecl methodDecl = null;
		if(kind == AST.Kind.METHOD)
		{
			try
			{
				methodDecl = (JCTree.JCMethodDecl) annotationNode.up().get();
				classNode = annotationNode.up().up();
				fieldName = "$" + methodDecl.name.toString() + "$lazyValue";
				paramType = (JCTree.JCExpression) methodDecl.getReturnType();
			}
			catch(Exception e)
			{
				e.printStackTrace();
				return;
			}
		}
		else
		{
			return;
		}

		injectLazyField(classNode, treeMaker, fieldName, paramType, methodDecl, notNull);
		changeMethodReturn(classNode, treeMaker, fieldName, methodDecl);
	}

	private static void changeMethodReturn(JavacNode classNode, TreeMaker treeMaker, String fieldName, JCTree.JCMethodDecl methodDecl)
	{
		JCTree.JCMethodInvocation call = treeMaker.Apply(List.<JCTree.JCExpression>nil(), chainDotsString(classNode, fieldName + ".getValue"),
				List.<JCTree.JCExpression>nil());

		JCTree.JCReturn aReturn = treeMaker.Return(call);
		methodDecl.body = treeMaker.Block(0, List.<JCTree.JCStatement>of(aReturn));
	}

	private static void injectLazyField(
			JavacNode classNode, TreeMaker treeMaker, String name, JCTree.JCExpression paramType, JCTree.JCMethodDecl methodDecl, boolean notNull)
	{
		String type = notNull ? "com.intellij.openapi.util.NotNullLazyValue" : "com.intellij.openapi.util.NullableLazyValue";

		JCTree.JCTypeApply jcTypeApply = treeMaker.TypeApply(chainDotsString(classNode, type), List.of(paramType));

		JCTree.JCVariableDecl instanceField = treeMaker.VarDef(mods(treeMaker, classNode, methodDecl),
				classNode.toName(name), jcTypeApply, null);

		JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(treeMaker.Modifiers(Modifier.PUBLIC), classNode.toName("compute"), paramType,
				List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>nil(), List.<JCTree.JCExpression>nil(), methodDecl.body, null);

		JCTree.JCClassDecl anonymClassDecl = treeMaker.AnonymousClassDef(treeMaker.Modifiers(0), List.<JCTree>of(jcMethodDecl));

		instanceField.init = treeMaker.NewClass(null, List.<JCTree.JCExpression>nil(), jcTypeApply, List.<JCTree.JCExpression>nil(),
				anonymClassDecl);

		injectField(classNode, instanceField);
	}

	public static JCTree.JCModifiers mods(TreeMaker treeMaker, JavacNode classNode, JCTree.JCMethodDecl methodDecl)
	{
		final JCTree.JCAnnotation notNullAnnotationDecl = treeMaker.Annotation(chainDotsString(classNode, NotNull.class.getName()),
				List.<JCTree.JCExpression>nil());

		int mods = Modifier.PRIVATE | Modifier.FINAL;
		if((methodDecl.mods.flags & Modifier.STATIC) != 0)
		{
			mods |= Modifier.STATIC;
		}
		return treeMaker.Modifiers(mods, List.<JCTree.JCAnnotation>of(notNullAnnotationDecl));
	}
}
