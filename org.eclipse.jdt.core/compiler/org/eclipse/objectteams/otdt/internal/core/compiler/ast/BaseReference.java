/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 *
 * Copyright 2004, 2006 Fraunhofer Gesellschaft, Munich, Germany,
 * for its Fraunhofer Institute for Computer Architecture and Software
 * Technology (FIRST), Berlin, Germany and Technical University Berlin,
 * Germany.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * $Id: BaseReference.java 23401 2010-02-02 23:56:05Z stephan $
 *
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 *
 * Contributors:
 * Fraunhofer FIRST - Initial API and implementation
 * Technical University Berlin - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otdt.internal.core.compiler.ast;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.codegen.CodeStream;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions.WeavingScheme;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.AstGenerator;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.RoleTypeCreator;

/**
 * NEW for OTDT:
 *
 * A special this-reference for base calls in callin-methods.
 *
 * @author macwitte
 *
 * Markus Witte for ObjectTeams
 */
public class BaseReference extends ThisReference {
	private Expression _wrappee = null;

	public BaseReference(int start, int end) {
		super(start, end);
	}
	
	@Override
	public boolean isImplicitThis() {
		return false;
	}

	/**
	 * Adjust the receiver of the enclosing base call.
	 *
	 * @param enclosingType
	 * @param isStatic
	 * @param outerCallinMethod
	 * @param gen
	 * @param weavingScheme which weaver to compile for
	 * @return the exact role class containing this base call (respecting local types).
	 */
	ReferenceBinding adjustReceiver(
			ReferenceBinding enclosingType,
			boolean isStatic,
			MethodDeclaration outerCallinMethod,
			AstGenerator gen,
			WeavingScheme weavingScheme)
	{
		boolean redirectToTeam = isStatic || (weavingScheme == WeavingScheme.OTDRE); // _OT$callNext is found via MyTeam.this
		
		if (outerCallinMethod != null) {
			// use "R.this" in order to point to the correct class, direct enclosing is a local class.
	        ReferenceBinding enclosingRole = outerCallinMethod.binding.declaringClass;
//	        ReferenceBinding receiverType = redirectToTeam?
//						        				enclosingRole.enclosingType() :
//						        				enclosingRole;
						        				
			this._wrappee = gen.singleTypeReference(enclosingRole.internalName());
//			this._wrappee = gen.qualifiedTypeReference(enclosingRole.baseclass.compoundName); //gen.qualifiedTypeReference(CharOperation.splitOn('.', baseCallTypeName));//new char[][] { dynamicCallinBootstrapTypeName });
	        return enclosingRole;
	        
		} else if (redirectToTeam) {
			// use MyTeam.this:
			//TODO Lars
	        ReferenceBinding receiverType = enclosingType.enclosingType();
	        if (receiverType != null) // null happens when callin method is not inside a role
	        	this._wrappee = gen.qualifiedThisReference(receiverType);
//	        	this._wrappee = //gen.qualifiedTypeReference(CharOperation.splitOn('.', baseCallTypeName)); //new char[][] { dynamicCallinBootstrapTypeName });
		}
		return enclosingType;
	}


	@Override
	public TypeBinding resolveType(BlockScope scope) {
		if (this._wrappee != null) {
			this.resolvedType = this._wrappee.resolveType(scope);
			if (this.resolvedType instanceof ReferenceBinding && this.resolvedType.isValidBinding())
				this.resolvedType = ((ReferenceBinding)this.resolvedType).getRealClass(); // base call surrogate is only in the class part
			this.constant = Constant.NotAConstant;
			if (this._wrappee.isTypeReference())
				this.bits |= Binding.TYPE;
		} else {
			// ensure 'base' is resolvable even in a static context (which the super implementation cannot)
			// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=331669
			this.constant = Constant.NotAConstant;
			this.resolvedType = RoleTypeCreator.maybeWrapUnqualifiedRoleType(scope, scope.enclosingReceiverType(), this);
		}
		return this.resolvedType;
	}

	@Override
	public void generateCode(BlockScope scope, CodeStream codeStream) {
		if (this._wrappee != null)
			this._wrappee.generateCode(scope, codeStream);
		else
			super.generateCode(scope, codeStream);
	}

	@Override
	public void generateCode(BlockScope scope, CodeStream codeStream, boolean valueRequired) {
		if (this._wrappee != null)
			this._wrappee.generateCode(scope, codeStream, valueRequired);
		else
			super.generateCode(scope, codeStream, valueRequired);
	}

	@Override
	public StringBuffer printExpression(int indent, StringBuffer output)
	{

	    output.append("base"); //$NON-NLS-1$
	    return output;
	}

	@Override
	public void traverse(
		ASTVisitor visitor,
		BlockScope blockScope) {
		visitor.visit(this, blockScope);
		visitor.endVisit(this, blockScope);
	}

	public boolean isQualified() {
		return true;
		//return (this._wrappee instanceof QualifiedThisReference);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof BaseReference) {
			return ((BaseReference) obj)._wrappee == this._wrappee;
		}
		return super.equals(obj);
	}
}
