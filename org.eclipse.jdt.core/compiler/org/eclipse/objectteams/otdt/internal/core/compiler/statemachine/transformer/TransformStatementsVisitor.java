/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 *
 * Copyright 2003, 2015 Fraunhofer Gesellschaft, Munich, Germany,
 * for its Fraunhofer Institute for Computer Architecture and Software
 * Technology (FIRST), Berlin, Germany and Technical University Berlin,
 * Germany.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * $Id: TransformStatementsVisitor.java 23416 2010-02-03 19:59:31Z stephan $
 *
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 *
 * Contributors:
 * Fraunhofer FIRST - Initial API and implementation
 * Technical University Berlin - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otdt.internal.core.compiler.statemachine.transformer;

import java.util.Stack;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ArrayAllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.eclipse.jdt.internal.compiler.ast.QualifiedThisReference;
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions.WeavingScheme;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.objectteams.otdt.core.compiler.IOTConstants;
import org.eclipse.objectteams.otdt.core.exceptions.InternalCompilerError;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.BaseCallMessageSend;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.BaseReference;
import org.eclipse.objectteams.otdt.internal.core.compiler.control.ITranslationStates;
import org.eclipse.objectteams.otdt.internal.core.compiler.control.StateHelper;
import org.eclipse.objectteams.otdt.internal.core.compiler.mappings.CallinImplementorDyn;
import org.eclipse.objectteams.otdt.internal.core.compiler.model.MethodModel;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.AstGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.TSuperHelper;


/**
 * This visitor performs several replacements within method bodies etc.
 * None of these replacements require resolved types or other non-local information.
 *
 * (1) Each tsuper(...) call -> this(..., markerArg)
 *     Works for constructor calls only.
 * (2) Adjust according to signature enhancing of callin methods:
 *     - 'recursive' calls need more arguments
 *     - return expressions need to be generalized wrt their type.
 *
 * Linking local types to role models is now in RecordLocalTypesVisitor -
 * WHY: recording local types MUST happen in any case, while TransformStatementsVisitor
 *       refuses to operate on types with ignoreFurtherInvestigation (see TypeDeclaration.traverse()).
 *
 * @author stephan
 * @version $Id: TransformStatementsVisitor.java 23416 2010-02-03 19:59:31Z stephan $
 */
public class TransformStatementsVisitor
    extends StackTransformStatementsVisitor
    implements IOTConstants
{

    // -- fields and methods for scope management ---
    private Stack<MethodDeclaration> _methodDeclarationStack = new Stack<MethodDeclaration>();
    private boolean isLocalTypeInCallin = false;

    private WeavingScheme weavingScheme;
    
    public TransformStatementsVisitor(WeavingScheme weavingScheme) {
    	this.weavingScheme = weavingScheme;
    }

    /**
     * If methodDeclaration is a callin method remember it for translating base calls.
     * Only remember methodDeclarations with ignoreFurtherInvestigation==false to
     * avoid NPEs and spurious error messages.
     *
     * @param methodDeclaration
     * @param localInCallin if the method is the enclosing (callin?) method of a local type
     */
	public void checkPushCallinMethod(AbstractMethodDeclaration methodDeclaration, boolean localInCallin) {
		if (   isGoodCallin(methodDeclaration))
		{
    		this._methodDeclarationStack.push((MethodDeclaration)methodDeclaration);
    		this.isLocalTypeInCallin = localInCallin;
		}
	}

	private boolean isGoodCallin(AbstractMethodDeclaration methodDeclaration) {
		return methodDeclaration != null
			&& !methodDeclaration.ignoreFurtherInvestigation
			&& methodDeclaration.isCallin()
			&& methodDeclaration.binding != null
			&& methodDeclaration.binding.isCallin(); // binding's callin flag was reset if callin in illegal context
	}

	protected boolean checkPopCallinMethod(AbstractMethodDeclaration currentMethod) {
		if (   !this._methodDeclarationStack.isEmpty()
			&& this._methodDeclarationStack.peek() == currentMethod)
		{
			assert currentMethod.isCallin();
			this._methodDeclarationStack.pop();
			return true;
		}
		return false;
	}

    // visit member type
    @Override
	public boolean visit (TypeDeclaration type, ClassScope scope)
    {
        if (type.isTeam())
            if (StateHelper.hasState(type.binding, ITranslationStates.STATE_STATEMENTS_TRANSFORMED))
                return false; // don't descend again
        return true;
    }

    /**
     * Within a tsuper constructor call -> add marker argument
     */
    @Override
	public boolean visit (ExplicitConstructorCall call, BlockScope scope)
    {
        if (call.isTsuperAccess())
        {
            call.arguments = TSuperHelper.addMarkerArgument(
                    null/*qualification*/, call, call.arguments, scope);
        }
        return true;
    }

    // === Adjustments following enhancement of callin method signatures:
    @Override
    public boolean visit(BaseCallMessageSend messageSend, BlockScope scope) {
    	if (!this._methodDeclarationStack.isEmpty())
    		messageSend.prepareSuperAccess(this.weavingScheme, this._methodDeclarationStack.peek(), scope);
		messageSend.bits |= ASTNode.HasBeenTransformed; // only the outer has been transformed so far.
    	return true;
    }
    
    /** May need to add arguments to a 'recursive' callin message send. */
    @Override
    public boolean visit(MessageSend messageSend, BlockScope scope) {
    	// scope is not reliable at this point due to unset scopes of block statements like "for"
    	if (this._methodDeclarationStack.isEmpty())
    		return true;
		MethodDeclaration methodDecl = this._methodDeclarationStack.peek();
    	boolean isBaseCall = messageSend.receiver instanceof BaseReference;
		if (   methodDecl.isCallin()
    		&& isRecursiveCall(methodDecl, messageSend, isBaseCall))
    	{
    		// argument enhancing within callin methods:
    		Expression[] args = messageSend.arguments;
    		if (isBaseCall) {
	    		switch (this.weavingScheme) {
	    			case OTDRE:
		    			break;
	    			case OTRE:
		    			if (args != null) {
		    				int len = args.length;
		    				if (methodDecl.isStatic()) // chop of premature isSuperAccess flag:
		    					System.arraycopy(args, 1, args=new Expression[len-1], 0, len-1);
		    			}	
	    		}
    		}
			messageSend.arguments = MethodSignatureEnhancer.enhanceArguments(args, messageSend.sourceEnd+1, this.weavingScheme);
			messageSend.bits |= ASTNode.HasBeenTransformed; // mark only when args have really been enhanced
    	}
    	return true;
    }

    /* these are considered as recursive calls: base calls, this/super/tsuper calls. */
    private boolean isRecursiveCall(MethodDeclaration callinMethod, MessageSend messageSend, boolean isBaseCall) {
    	if (!(messageSend.receiver instanceof ThisReference))
    		return false;
    	if (messageSend.receiver instanceof QualifiedThisReference)
    		return false;
    	if (!CharOperation.equals(callinMethod.selector, messageSend.selector) && !CharOperation.equals(CallinImplementorDyn.OT_CALL_NEXT, messageSend.selector))
    		return false;
    	if (callinMethod.arguments == null)
    		return false;
    	int sendArgs = messageSend.arguments == null ? 0 : messageSend.arguments.length;
    	if (this.weavingScheme == WeavingScheme.OTDRE) {
    		// is already packed in BCMS.prepareSuperAccess(), fetch number of arguments from the packed array in pos [0]
    		if (sendArgs > 0) {
    			Expression firstArg = messageSend.arguments[0];
				if (firstArg instanceof NullLiteral)
    				sendArgs = 0;
    			else if (firstArg instanceof ArrayAllocationExpression)
    				sendArgs = ((ArrayAllocationExpression) firstArg).initializer.expressions.length;
    		}
    	}
    	sendArgs += MethodSignatureEnhancer.getEnhancingArgLen(this.weavingScheme);
    	if (isBaseCall && this.weavingScheme == WeavingScheme.OTRE)
    		sendArgs--; // don't count the isSuperAccess flag
    	return sendArgs == callinMethod.arguments.length;
    }

    /** May need to 'generalize' a return expression. */
    @Override
    public boolean visit(ReturnStatement returnStatement, BlockScope scope) {
    	if (this._methodDeclarationStack.isEmpty() || this.isLocalTypeInCallin)
    		return true;
    	MethodDeclaration methodDecl = this._methodDeclarationStack.peek();
    	if (!isGoodCallin(methodDecl))
    		return true;
    	if (scope != null && scope.methodScope() != methodDecl.scope)
    		return true; // method in a nested type, not the callin itself
    	TypeBinding returnType = MethodModel.getReturnType(methodDecl.binding);
    	if (!returnType.isBaseType())
    		return true;
    	AstGenerator gen = new AstGenerator(returnStatement.sourceStart, returnStatement.sourceEnd);
    	if (returnType == TypeBinding.VOID) {
    		if (returnStatement.expression != null) {
    			if (scope == null && !this._methodDeclarationStack.isEmpty())
    				scope = this._methodDeclarationStack.peek().scope;
    			if (scope != null)
    				scope.problemReporter().attemptToReturnNonVoidExpression(returnStatement, returnType);
    			else
    				throw new InternalCompilerError("Missing scope for error reporting"); //$NON-NLS-1$
    		} else {
    			// return stored value:
    			//TODO Lars: Check if we still need OT_RESULT
//    			returnStatement.expression = gen.singleNameReference(OT_RESULT);
    		}
    	}
    	return true;
    }

    @Override
    public boolean visit(MethodDeclaration methodDeclaration, ClassScope scope) {
    	methodDeclaration.bits |= ASTNode.HasBeenTransformed;
    	checkPushCallinMethod(methodDeclaration, false);
    	return true;
    }

    @Override
    public void endVisit(MethodDeclaration methodDecl, ClassScope scope) {
    	if (checkPopCallinMethod(methodDecl)) {
//    		// assume method had no errors (else it would not have been pushed)
//    		TypeBinding returnType = MethodModel.getReturnType(methodDecl.binding);
//    		// return type was already adjusted by SourceTypeBinding.resolveTypesFor()->MethodSignatureEnhance.generalizeReturnType()
//    		if (   returnType == TypeBinding.VOID
//    			&& !methodDecl.isGenerated
//    			&& !methodDecl.isCopied
//    			&& !methodDecl.isAbstract())
//    		{
//    			AstGenerator gen = new AstGenerator(methodDecl.bodyEnd, methodDecl.bodyEnd);
//    			if (methodDecl.statements == null) {
//    				methodDecl.setStatements(new Statement[] {
//    					gen.returnStatement(gen.nullLiteral(), true/*synthetic*/)
//    				});
//    			} else {
//	    			// bracket body with:
//	    			// Object _OT$result = null;
//	    			// ... ( meanwhile a BaseCallMessageSend might assign to _OT$result )
//	    			// return _OT$result;
//	    			int len = methodDecl.statements.length;
//	    			Statement[] newStatements = new Statement[len+2];
//	    			System.arraycopy(methodDecl.statements, 0, newStatements, 1, len);
//
//	    			//save source positions from AstGenerator (ike)
//	    	    	SourcePosition savePos = gen.getSourcePosition();
//
//	    			try {
//	    				//set to first line of this method (if any)
//	    				if (len > 0)
//	    					gen.setSourcePosition((((long)methodDecl.statements[0].sourceStart)<<32) + methodDecl.statements[0].sourceEnd);
//
//						//generate local variable (ike)
//						newStatements[0] = gen.localVariable(OT_RESULT, scope.getJavaLangObject(), gen.nullLiteral());
//
//	    			} finally {
//	    				//restore source postions (ike)
//	    				gen.setSourcePosition(savePos);
//						}
//
//	    			newStatements[len+1] = gen.returnStatement(gen.singleNameReference(OT_RESULT), true/*synthetic*/);
//	    			methodDecl.setStatements(newStatements);
//    			}
//    		}
    	}
    	super.endVisit(methodDecl, scope);
    }

    /**
     * Cut traversal for types without a scope.
     */
    @Override
	public boolean visit(TypeDeclaration td, BlockScope scope) {
    	if((td.bits & ASTNode.IsLocalType)!=0)
    		if (td.scope == null)
    			return false; // don't descend further, local type is not in a useful state yet.

    	return true;
    }

	// Late transformation for callins within types with errors,
	// required for resilience regarding base calls.
	public static void checkTransformStatements(AbstractMethodDeclaration method)
	{
		ClassScope scope = method.scope.classScope();
		if (   scope.referenceContext.ignoreFurtherInvestigation
			&& method instanceof MethodDeclaration
			&& method.isCallin()
			&& (method.bits & ASTNode.HasBeenTransformed) == 0)
		{
			// but if class has errors the visitor bailed out.
			// need to transform before changing the selector
			method.traverse(new TransformStatementsVisitor(scope.compilerOptions().weavingScheme), scope);
		}
	}
}