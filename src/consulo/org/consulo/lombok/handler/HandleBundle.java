package org.consulo.lombok.handler;

import static lombok.javac.handlers.JavacHandlerUtil.chainDotsString;
import static lombok.javac.handlers.JavacHandlerUtil.injectField;
import static lombok.javac.handlers.JavacHandlerUtil.injectMethod;

import java.lang.reflect.Modifier;

import org.consulo.lombok.annotations.Bundle;
import org.jetbrains.annotations.NotNull;
import org.mangosdk.spi.ProviderFor;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;

/**
 * @author VISTALL
 * @since 17:17/21.10.13
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleBundle extends JavacAnnotationHandler<Bundle>
{
	@Override
	public void handle(AnnotationValues<Bundle> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode)
	{
		JavacNode classNode = annotationNode.up();

		final TreeMaker treeMaker = annotationNode.getTreeMaker();

		final JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) classNode.get();

		classDecl.extending = chainDotsString(classNode, "com.intellij.AbstractBundle");

		JCTree.JCExpression thisType = chainDotsString(classNode, classDecl.name.toString());

		generateOurInstance(classNode, treeMaker, thisType);
		generateConstructor(annotation, classNode, treeMaker, thisType);
		generateMessage0(annotation, classNode, treeMaker, thisType);
		generateMessage1(annotation, classNode, treeMaker, thisType);
	}


	private void generateOurInstance(JavacNode classNode, TreeMaker treeMaker, JCTree.JCExpression thisType)
	{
		JCTree.JCNewClass jcNewClass = treeMaker.NewClass(null, List.<JCTree.JCExpression>nil(), thisType, List.<JCTree.JCExpression>nil(), null);
		JCTree.JCVariableDecl ourInstance = treeMaker.VarDef(treeMaker.Modifiers(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL), classNode.toName("ourInstance"), thisType, jcNewClass);

		injectField(classNode, ourInstance);
	}

	private void generateConstructor(AnnotationValues<Bundle> annotation, JavacNode classNode, TreeMaker treeMaker, JCTree.JCExpression thisType)
	{
		JCTree.JCMethodInvocation aSuper = treeMaker.Apply(List.<JCTree.JCExpression>nil(), treeMaker.Ident(classNode.toName("super")), List.<JCTree.JCExpression>of(treeMaker.Literal(annotation.getInstance().value())));

		JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(treeMaker.Modifiers(Modifier.PRIVATE), classNode.toName("<init>"), null, List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>nil(),
				List.<JCTree.JCExpression>nil(), treeMaker.Block(0, List.<JCTree.JCStatement>of(treeMaker.Exec(aSuper), treeMaker.Return(null))), null);

		injectMethod(classNode, jcMethodDecl);
	}

	private void generateMessage0(AnnotationValues<Bundle> annotation, JavacNode classNode, TreeMaker treeMaker, JCTree.JCExpression thisType)
	{
		JCTree.JCMethodInvocation call = treeMaker.Apply(List.<JCTree.JCExpression>nil(), chainDotsString(classNode, "ourInstance.getMessage"), List.<JCTree.JCExpression>of(treeMaker.Ident(classNode.toName("key"))));

		JCTree.JCAssign property = treeMaker.Assign(treeMaker.Ident(classNode.toName("resourceBundle")), treeMaker.Literal(annotation.getInstance().value()));
		JCTree.JCAnnotation propertyKeyAnnotation = treeMaker.Annotation(chainDotsString(classNode, "org.jetbrains.annotations.PropertyKey"), List.<JCTree.JCExpression>of(property));

		JCTree.JCExpression stringType = chainDotsString(classNode, String.class.getName());

		JCTree.JCVariableDecl param = treeMaker.VarDef(treeMaker.Modifiers(0, List.<JCTree.JCAnnotation>of(propertyKeyAnnotation)), classNode.toName("key"), stringType, null);

		JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(HandleQService.createModifierListWithNotNull(treeMaker, classNode, Modifier.PUBLIC | Modifier.STATIC), classNode.toName("message"), stringType,
				List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>of(param), List.<JCTree.JCExpression>nil(), treeMaker.Block(0, List.<JCTree.JCStatement>of(treeMaker.Return(call))), null);


		injectMethod(classNode, jcMethodDecl);
	}

	private void generateMessage1(AnnotationValues<Bundle> annotation, JavacNode classNode, TreeMaker treeMaker, JCTree.JCExpression thisType)
	{
		JCTree.JCMethodInvocation call = treeMaker.Apply(List.<JCTree.JCExpression>nil(), chainDotsString(classNode, "ourInstance.getMessage"), List.<JCTree.JCExpression>of(treeMaker.Ident(classNode.toName("key")),
				treeMaker.Ident(classNode.toName("args"))));

		JCTree.JCAssign property = treeMaker.Assign(treeMaker.Ident(classNode.toName("resourceBundle")), treeMaker.Literal(annotation.getInstance().value()));
		JCTree.JCAnnotation propertyKeyAnnotation = treeMaker.Annotation(chainDotsString(classNode, "org.jetbrains.annotations.PropertyKey"), List.<JCTree.JCExpression>of(property));

		JCTree.JCExpression stringType = chainDotsString(classNode, String.class.getName());

		JCTree.JCVariableDecl param1 = treeMaker.VarDef(treeMaker.Modifiers(0, List.<JCTree.JCAnnotation>of(propertyKeyAnnotation)), classNode.toName("key"), stringType, null);
		JCTree.JCVariableDecl param2 = treeMaker.VarDef(treeMaker.Modifiers(Flags.VARARGS), classNode.toName("args"), treeMaker.TypeArray(chainDotsString(classNode, Object.class.getName())), null);

		JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(HandleQService.createModifierListWithNotNull(treeMaker, classNode, Modifier.PUBLIC | Modifier.STATIC), classNode.toName("message"), stringType,
				List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>of(param1, param2), List.<JCTree.JCExpression>nil(), treeMaker.Block(0, List.<JCTree.JCStatement>of(treeMaker.Return(call))), null);


		injectMethod(classNode, jcMethodDecl);
	}

	private static JCTree.JCModifiers createModifierListWithNotNull(TreeMaker treeMaker, JavacNode classNode, long val)
	{
		final JCTree.JCAnnotation notNullAnnotationDecl = treeMaker.Annotation(chainDotsString(classNode, NotNull.class.getName()), List.<JCTree.JCExpression>nil());
		return treeMaker.Modifiers(val, List.<JCTree.JCAnnotation>of(notNullAnnotationDecl));
	}
}
