package org.consulo.lombok.handler;

import static lombok.javac.Javac.CTC_INT;
import static lombok.javac.handlers.JavacHandlerUtil.chainDots;
import static lombok.javac.handlers.JavacHandlerUtil.chainDotsString;
import static lombok.javac.handlers.JavacHandlerUtil.injectField;

import java.lang.reflect.Modifier;

import org.consulo.lombok.annotations.ArrayFactoryFields;
import org.jetbrains.annotations.NotNull;
import org.mangosdk.spi.ProviderFor;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import lombok.core.AnnotationValues;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;

/**
 * @author VISTALL
 * @since 15:08/21.10.13
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleArrayFactoryFields extends JavacAnnotationHandler<ArrayFactoryFields>
{
	@Override
	public void handle(AnnotationValues<ArrayFactoryFields> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode)
	{
		JavacNode classNode = annotationNode.up();
		final TreeMaker treeMaker = annotationNode.getTreeMaker();

		final JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) classNode.get();

		makeEmptyArrayField(classNode, treeMaker, classDecl);

		makeArrayFactoryField(classNode, treeMaker, classDecl);
	}

	private void makeArrayFactoryField(JavacNode classNode, TreeMaker treeMaker, JCTree.JCClassDecl classDecl)
	{
		JCTree.JCExpression arrayFactoryExp = chainDots(classNode, "com.intellij.util.ArrayFactory");
		JCTree.JCExpression paramTypeExp = chainDotsString(classNode, classDecl.name.toString());

		JCTree.JCTypeApply jcTypeApply = treeMaker.TypeApply(arrayFactoryExp, List.of(paramTypeExp));

		JCTree.JCVariableDecl arrayFactoryField = treeMaker.VarDef(createModifierListWithNotNull(treeMaker, classNode),
				classNode.toName("ARRAY_FACTORY"), jcTypeApply, null);

		JCTree.JCExpression typeExp = chainDotsString(classNode, classDecl.name.toString());

		JCTree.JCBinary ifCheckExp = treeMaker.Binary(Javac.CTC_EQUAL, chainDots(classNode, "count"), treeMaker.Literal(TypeTags.INT, 0));

		JCTree.JCConditional conditional = treeMaker.Conditional(ifCheckExp, chainDots(classNode, "EMPTY_ARRAY"), treeMaker.NewArray(typeExp, List.<JCTree.JCExpression>of(chainDots(classNode, "count")), null));

		JCTree.JCBlock block = treeMaker.Block(0, List.<JCTree.JCStatement>of(treeMaker.Return(conditional)));

		JCTree.JCVariableDecl p = treeMaker.VarDef(treeMaker.Modifiers(0), classNode.toName("count"), chainDots(classNode, "int"), null);
		JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(
				treeMaker.Modifiers(Modifier.PUBLIC),
				classNode.toName("create"),
				treeMaker.TypeArray(chainDots(classNode, "T")),
				List.<JCTree.JCTypeParameter>nil(),
				List.<JCTree.JCVariableDecl>of(p),
				List.<JCTree.JCExpression>nil(),
				block,
				null);

		JCTree.JCClassDecl anonymClassDecl = treeMaker.AnonymousClassDef(treeMaker.Modifiers(0), List.<JCTree>of(jcMethodDecl));

		JCTree.JCNewClass jcNewClass = treeMaker.NewClass(null, List.<JCTree.JCExpression>nil(), jcTypeApply, List.<JCTree.JCExpression>nil(), anonymClassDecl);

		arrayFactoryField.init = jcNewClass;

		injectField(classNode, arrayFactoryField);
	}

	private void makeEmptyArrayField(JavacNode classNode, TreeMaker treeMaker, JCTree.JCClassDecl classDecl)
	{
		JCTree.JCExpression typeExp = chainDotsString(classNode, classDecl.name.toString());

		JCTree.JCNewArray jcNewArray = treeMaker.NewArray(typeExp, List.<JCTree.JCExpression>of(treeMaker.Literal(CTC_INT, 0)), null);

		JCTree.JCArrayTypeTree jcArrayTypeTree = treeMaker.TypeArray(typeExp);

		JCTree.JCVariableDecl emptyArrayField = treeMaker.VarDef(createModifierListWithNotNull(treeMaker, classNode), classNode.toName("EMPTY_ARRAY"), jcArrayTypeTree, jcNewArray);

		injectField(classNode, emptyArrayField);
	}

	public static JCTree.JCModifiers createModifierListWithNotNull(TreeMaker treeMaker, JavacNode classNode)
	{
		final JCTree.JCAnnotation notNullAnnotationDecl = treeMaker.Annotation(chainDotsString(classNode, NotNull.class.getName()), List.<JCTree.JCExpression>nil());
		return treeMaker.Modifiers(Modifier.STATIC | Modifier.PUBLIC | Modifier.FINAL, List.<JCTree.JCAnnotation>of(notNullAnnotationDecl));
	}
}
