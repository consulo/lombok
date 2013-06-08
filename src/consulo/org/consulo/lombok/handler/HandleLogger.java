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
import static lombok.javac.handlers.JavacHandlerUtil.deleteAnnotationIfNeccessary;
import static lombok.javac.handlers.JavacHandlerUtil.fieldExists;
import static lombok.javac.handlers.JavacHandlerUtil.injectField;
import static lombok.javac.handlers.JavacHandlerUtil.recursiveSetGeneratedBy;

import org.consulo.lombok.annotations.Logger;
import org.mangosdk.spi.ProviderFor;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.handlers.JavacHandlerUtil;

/**
 * @author VISTALL
 * @since 11:42/03.06.13
 *
 * @see lombok.javac.handlers.HandleLog
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleLogger extends JavacAnnotationHandler<Logger>
{
	private static final String LOG_CLASS = "com.intellij.openapi.diagnostic.Logger";
	private static final String LOG_METHOD = LOG_CLASS + ".getInstance";
	private static final String LOG_FIELD_NAME = "LOGGER";

	@Override
	public void handle(AnnotationValues<Logger> annotationValues, JCTree.JCAnnotation jcAnnotation, JavacNode javacNode)
	{
		processAnnotation(annotationValues,  javacNode);
	}

	public static void processAnnotation(AnnotationValues<?> annotation, JavacNode annotationNode)
	{
		deleteAnnotationIfNeccessary(annotationNode, Logger.class);

		JavacNode typeNode = annotationNode.up();
		switch(typeNode.getKind())
		{
			case TYPE:
				if((((JCTree.JCClassDecl) typeNode.get()).mods.flags & Flags.INTERFACE) != 0)
				{
					annotationNode.addError("@Logger is legal only on classes and enums.");
					return;
				}

				if(fieldExists(LOG_FIELD_NAME, typeNode) != JavacHandlerUtil.MemberExistsResult.NOT_EXISTS)
				{
					annotationNode.addWarning(String.format("Field '%s' already exists.", LOG_FIELD_NAME));
					return;
				}

				JCTree.JCFieldAccess loggingType = selfType(typeNode);
				createField(typeNode, loggingType, annotationNode.get());
				break;
			default:
				annotationNode.addError("@Logger is legal only on types.");
				break;
		}
	}

	private static JCTree.JCFieldAccess selfType(JavacNode typeNode)
	{
		TreeMaker maker = typeNode.getTreeMaker();
		Name name = ((JCTree.JCClassDecl) typeNode.get()).name;
		return maker.Select(maker.Ident(name), typeNode.toName("class"));
	}

	private static boolean createField(JavacNode typeNode, JCTree.JCFieldAccess loggingType, JCTree source)
	{
		TreeMaker maker = typeNode.getTreeMaker();

		// private static final <loggerType> log = <factoryMethod>(<parameter>);
		JCTree.JCExpression loggerType = chainDotsString(typeNode, LOG_CLASS);
		JCTree.JCExpression factoryMethod = chainDotsString(typeNode, LOG_METHOD);

		JCTree.JCMethodInvocation factoryMethodCall = maker.Apply(List.<JCTree.JCExpression>nil(), factoryMethod, List.<JCTree.JCExpression>of(loggingType));

		JCTree.JCVariableDecl fieldDecl = recursiveSetGeneratedBy(maker.VarDef(maker.Modifiers(Flags.PRIVATE | Flags.FINAL | Flags.STATIC), typeNode.toName(LOG_FIELD_NAME), loggerType, factoryMethodCall), source);

		injectField(typeNode, fieldDecl);
		return true;
	}
}
