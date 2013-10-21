/*
 * Copyright 2013 Consulo.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.consulo.lombok.handler;

import static lombok.javac.handlers.JavacHandlerUtil.chainDotsString;

import java.lang.reflect.Modifier;

import org.consulo.lombok.annotations.ApplicationService;
import org.consulo.lombok.annotations.ModuleService;
import org.consulo.lombok.annotations.ProjectService;
import org.mangosdk.spi.ProviderFor;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.NonNull;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.handlers.JavacHandlerUtil;

/**
 * @author VISTALL
 * @since 16:44/03.06.13
 */
public class HandleQService
{
	public static enum ServiceType
	{
		Application,
		Project
				{
					@Override
					public List<JCTree.JCVariableDecl> getArguments(TreeMaker treeMaker, JavacNode javacNode)
					{
						final JCTree.JCExpression jcExpression = chainDotsString(javacNode, "com.intellij.openapi.project.Project");

						final JCTree.JCVariableDecl varDec = treeMaker.VarDef(createModifierListWithNotNull(treeMaker, javacNode, 0), javacNode.toName("project"), jcExpression, null);
						return List.of(varDec);
					}

					@Override
					public List<JCTree.JCExpression> preAppendArguments(List<JCTree.JCExpression> expressions, TreeMaker treeMaker, JCTree.JCClassDecl classDecl, JavacNode classNode)
					{
						expressions = expressions.append(chainDotsString(classNode, "project"));
						expressions = super.preAppendArguments(expressions, treeMaker, classDecl, classNode);
						return expressions;
					}
				},
		Module
				{
					@Override
					public List<JCTree.JCVariableDecl> getArguments(TreeMaker treeMaker, JavacNode javacNode)
					{
						final JCTree.JCExpression jcExpression = chainDotsString(javacNode, "com.intellij.openapi.module.Module");

						final JCTree.JCVariableDecl varDec = treeMaker.VarDef(createModifierListWithNotNull(treeMaker, javacNode, 0), javacNode.toName("module"), jcExpression, null);
						return List.of(varDec);
					}

					@Override
					public List<JCTree.JCExpression> preAppendArguments(List<JCTree.JCExpression> expressions, TreeMaker treeMaker, JCTree.JCClassDecl classDecl, JavacNode classNode)
					{
						expressions = expressions.append(chainDotsString(classNode, "module"));
						expressions = super.preAppendArguments(expressions, treeMaker, classDecl, classNode);
						return expressions;
					}

					@Override
					public String getServiceManagerQName()
					{
						return "com.intellij.openapi.module.ModuleServiceManager.getService";
					}
				};

		public List<JCTree.JCVariableDecl> getArguments(TreeMaker treeMaker, JavacNode javacNode)
		{
			return List.nil();
		}

		public List<JCTree.JCExpression> preAppendArguments(List<JCTree.JCExpression> expressions, TreeMaker treeMaker, JCTree.JCClassDecl classDecl, JavacNode classNode)
		{
			return expressions.append(getClassAccess(treeMaker, classDecl, classNode));
		}

		public String getServiceManagerQName()
		{
			return "com.intellij.openapi.components.ServiceManager.getService";
		}
	}

	@ProviderFor(JavacAnnotationHandler.class)
	public static class HandleApplicationService extends JavacAnnotationHandler<ApplicationService>
	{
		@Override
		public void handle(AnnotationValues<ApplicationService> annotationValues, JCTree.JCAnnotation jcAnnotation, JavacNode node)
		{
			make0(node, ServiceType.Application);
		}
	}

	@ProviderFor(JavacAnnotationHandler.class)
	public static class HandleProjectService extends JavacAnnotationHandler<ProjectService>
	{
		@Override
		public void handle(AnnotationValues<ProjectService> annotationValues, JCTree.JCAnnotation jcAnnotation, JavacNode node)
		{
			make0(node, ServiceType.Project);
		}
	}

	@ProviderFor(JavacAnnotationHandler.class)
	public static class HandleModuleService extends JavacAnnotationHandler<ModuleService>
	{
		@Override
		public void handle(AnnotationValues<ModuleService> annotationValues, JCTree.JCAnnotation jcAnnotation, JavacNode node)
		{
			make0(node, ServiceType.Module);
		}
	}

	private static void make0(JavacNode node, ServiceType serviceType)
	{
		JavacNode typeNode = node.up();
		switch(typeNode.getKind())
		{
			case TYPE:
				make(node, serviceType);
				break;
			default:
				node.addError("@#Service annotation is applicable only to classes");
				break;
		}
	}

	private static void make(JavacNode node, ServiceType serviceType)
	{
		final TreeMaker treeMaker = node.getTreeMaker();
		final JavacNode classNode = node.up();
		final JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) classNode.get();

		// if class is interface - make it abstract
		if((classDecl.mods.flags & Modifier.INTERFACE) != 0)
		{
			classNode.addError("Class cant be interface");
			return;
		}

		final JCTree.JCModifiers modifiers = createModifierListWithNotNull(treeMaker, classNode, Modifier.PUBLIC | Modifier.STATIC);

		final JCTree.JCExpression methodCallQName = chainDotsString(classNode, serviceType.getServiceManagerQName());

		List<JCTree.JCExpression> arguments = List.nil();
		arguments = serviceType.preAppendArguments(arguments, treeMaker, classDecl, classNode);

		final JCTree.JCMethodInvocation methodCallDecl = treeMaker.Apply(List.<JCTree.JCExpression>nil(), methodCallQName, arguments);

		final JCTree.JCReturn returnDecl = treeMaker.Return(methodCallDecl);
		final JCTree.JCBlock blockDecl = treeMaker.Block(0, List.<JCTree.JCStatement>of(returnDecl));

		final JCTree.JCMethodDecl decl = treeMaker.MethodDef(modifiers, classNode.toName("getInstance"), JavacHandlerUtil.chainDotsString(classNode, classDecl.name.toString()), List.<JCTree.JCTypeParameter>nil(), serviceType.getArguments(treeMaker, classNode), List.<JCTree.JCExpression>nil(), blockDecl, null);

		JavacHandlerUtil.injectMethod(classNode, decl);
	}

	private static JCTree.JCModifiers createModifierListWithNotNull(TreeMaker treeMaker, JavacNode classNode, long val)
	{
		final JCTree.JCAnnotation notNullAnnotationDecl = treeMaker.Annotation(chainDotsString(classNode, NonNull.class.getName()), List.<JCTree.JCExpression>nil());
		return treeMaker.Modifiers(val, List.<JCTree.JCAnnotation>of(notNullAnnotationDecl));
	}

	private static JCTree.JCFieldAccess getClassAccess(TreeMaker treeMaker, JCTree.JCClassDecl classDecl, JavacNode node)
	{
		Name name = classDecl.name;
		return treeMaker.Select(treeMaker.Ident(name), node.toName("class"));
	}
}
