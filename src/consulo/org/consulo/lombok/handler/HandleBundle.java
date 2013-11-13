package org.consulo.lombok.handler;

import static lombok.javac.handlers.JavacHandlerUtil.chainDotsString;
import static lombok.javac.handlers.JavacHandlerUtil.injectField;
import static lombok.javac.handlers.JavacHandlerUtil.injectMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.consulo.lombok.annotations.Bundle;
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
	private static Field ourExtendingField;

	private static void setExtending(JCTree.JCClassDecl classDecl, JCTree.JCExpression expression)
	{
		try
		{
			classDecl.extending = expression;
		}
		catch(NoSuchFieldError e) // this is fix for Java7 and later, due in Java6 'extending' have type JCTree, higher is JCTree.JCExpression
		{
			if(ourExtendingField == null)
			{
				try
				{
					ourExtendingField = JCTree.JCClassDecl.class.getField("extending");
				}
				catch(NoSuchFieldException e1)
				{
					throw new Error("No 'extending' field: " + System.getenv());
				}
			}

			try
			{
				ourExtendingField.set(classDecl, expression);
			}
			catch(IllegalAccessException e1)
			{
				throw new Error(e1);
			}
		}
	}

	@Override
	public void handle(AnnotationValues<Bundle> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode)
	{
		JavacNode classNode = annotationNode.up();

		final TreeMaker treeMaker = annotationNode.getTreeMaker();

		final JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) classNode.get();

		setExtending(classDecl, chainDotsString(classNode, "com.intellij.AbstractBundle"));

		JCTree.JCExpression thisType = chainDotsString(classNode, classDecl.name.toString());

		String resourceBundleValue = annotation.getInstance().value();
		if(resourceBundleValue.isEmpty())
		{
			resourceBundleValue = "messages." + classDecl.name.toString();
		}

		generateOurInstance(classNode, treeMaker, thisType);
		generateConstructor(annotation, classNode, treeMaker, thisType, resourceBundleValue);
		generateMessage0(annotation, classNode, treeMaker, thisType, resourceBundleValue);
		generateMessage1(annotation, classNode, treeMaker, thisType, resourceBundleValue);
	}


	private void generateOurInstance(JavacNode classNode, TreeMaker treeMaker, JCTree.JCExpression thisType)
	{
		JCTree.JCNewClass jcNewClass = treeMaker.NewClass(null, List.<JCTree.JCExpression>nil(), thisType, List.<JCTree.JCExpression>nil(), null);
		JCTree.JCVariableDecl ourInstance = treeMaker.VarDef(treeMaker.Modifiers(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL), classNode.toName("ourInstance"), thisType, jcNewClass);

		injectField(classNode, ourInstance);
	}

	private void generateConstructor(AnnotationValues<Bundle> annotation, JavacNode classNode, TreeMaker treeMaker, JCTree.JCExpression thisType, String resourceBundleValue)
	{
		JCTree.JCMethodInvocation aSuper = treeMaker.Apply(List.<JCTree.JCExpression>nil(), treeMaker.Ident(classNode.toName("super")), List.<JCTree.JCExpression>of(treeMaker.Literal(resourceBundleValue)));

		JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(treeMaker.Modifiers(Modifier.PRIVATE), classNode.toName("<init>"), null, List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>nil(),
				List.<JCTree.JCExpression>nil(), treeMaker.Block(0, List.<JCTree.JCStatement>of(treeMaker.Exec(aSuper), treeMaker.Return(null))), null);

		injectMethod(classNode, jcMethodDecl);
	}

	private void generateMessage0(AnnotationValues<Bundle> annotation, JavacNode classNode, TreeMaker treeMaker, JCTree.JCExpression thisType, String resourceBundleValue)
	{
		JCTree.JCMethodInvocation call = treeMaker.Apply(List.<JCTree.JCExpression>nil(), chainDotsString(classNode, "ourInstance.getMessage"), List.<JCTree.JCExpression>of(treeMaker.Ident(classNode.toName("key"))));

		JCTree.JCAssign property = treeMaker.Assign(treeMaker.Ident(classNode.toName("resourceBundle")), treeMaker.Literal(resourceBundleValue));
		JCTree.JCAnnotation propertyKeyAnnotation = treeMaker.Annotation(chainDotsString(classNode, "org.jetbrains.annotations.PropertyKey"), List.<JCTree.JCExpression>of(property));

		JCTree.JCExpression stringType = chainDotsString(classNode, String.class.getName());

		JCTree.JCVariableDecl param = treeMaker.VarDef(treeMaker.Modifiers(0, List.<JCTree.JCAnnotation>of(propertyKeyAnnotation)), classNode.toName("key"), stringType, null);

		JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(HandleQService.createModifierListWithNotNull(treeMaker, classNode, Modifier.PUBLIC | Modifier.STATIC), classNode.toName("message"), stringType,
				List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>of(param), List.<JCTree.JCExpression>nil(), treeMaker.Block(0, List.<JCTree.JCStatement>of(treeMaker.Return(call))), null);


		injectMethod(classNode, jcMethodDecl);
	}

	private void generateMessage1(AnnotationValues<Bundle> annotation, JavacNode classNode, TreeMaker treeMaker, JCTree.JCExpression thisType, String resourceBundleValue)
	{
		JCTree.JCMethodInvocation call = treeMaker.Apply(List.<JCTree.JCExpression>nil(), chainDotsString(classNode, "ourInstance.getMessage"), List.<JCTree.JCExpression>of(treeMaker.Ident(classNode.toName("key")),
				treeMaker.Ident(classNode.toName("args"))));

		JCTree.JCAssign property = treeMaker.Assign(treeMaker.Ident(classNode.toName("resourceBundle")), treeMaker.Literal(resourceBundleValue));
		JCTree.JCAnnotation propertyKeyAnnotation = treeMaker.Annotation(chainDotsString(classNode, "org.jetbrains.annotations.PropertyKey"), List.<JCTree.JCExpression>of(property));

		JCTree.JCExpression stringType = chainDotsString(classNode, String.class.getName());

		JCTree.JCVariableDecl param1 = treeMaker.VarDef(treeMaker.Modifiers(0, List.<JCTree.JCAnnotation>of(propertyKeyAnnotation)), classNode.toName("key"), stringType, null);
		JCTree.JCVariableDecl param2 = treeMaker.VarDef(treeMaker.Modifiers(Flags.VARARGS), classNode.toName("args"), treeMaker.TypeArray(chainDotsString(classNode, Object.class.getName())), null);

		JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(HandleQService.createModifierListWithNotNull(treeMaker, classNode, Modifier.PUBLIC | Modifier.STATIC), classNode.toName("message"), stringType,
				List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>of(param1, param2), List.<JCTree.JCExpression>nil(), treeMaker.Block(0, List.<JCTree.JCStatement>of(treeMaker.Return(call))), null);


		injectMethod(classNode, jcMethodDecl);
	}
}
