/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 *
 * Copyright 2003, 2006 Fraunhofer Gesellschaft, Munich, Germany,
 * for its Fraunhofer Institute for Computer Architecture and Software
 * Technology (FIRST), Berlin, Germany and Technical University Berlin,
 * Germany.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * $Id: CallinCalloutBinding.java 23416 2010-02-03 19:59:31Z stephan $
 *
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 *
 * Contributors:
 * Fraunhofer FIRST - Initial API and implementation
 * Technical University Berlin - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otdt.internal.core.compiler.lookup;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReasons;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding;
import org.eclipse.jdt.internal.compiler.parser.TerminalTokens;
import org.eclipse.objectteams.otdt.core.compiler.InferenceKind;
import org.eclipse.objectteams.otdt.internal.core.compiler.ast.CallinMappingDeclaration;
import org.eclipse.objectteams.otdt.internal.core.compiler.util.TypeAnalyzer;

/**
 * NEW for OTDT
 *
 *
 * @author mac
 * @version $Id: CallinCalloutBinding.java 23416 2010-02-03 19:59:31Z stephan $
 */
public class CallinCalloutBinding extends Binding
{
    //TODO(mkr) resolve mix of local constants and TerminalTokens
    public static final int CALLIN              = 1;
    public static final int CALLOUT             = 2;
	public static final int CALLOUT_OVERRIDE    = 3;

    public static final int REPLACE             = TerminalTokens.TokenNamereplace;
    public static final int AFTER               = TerminalTokens.TokenNameafter;
    public static final int BEFORE              = TerminalTokens.TokenNamebefore;

    public TypeVariableBinding[] typeVariables;
    public MethodBinding    _roleMethodBinding;
    public ReferenceBinding _declaringRoleClass;
    public MethodBinding[]  _baseMethods = Binding.NO_METHODS;
    public FieldBinding 	_baseField;
    public int              type;
    public int              callinModifier;  // TerminalTokens: before,after,replace
    public int              calloutModifier; // TerminalTokens: get or set
    public int 				declaredModifiers; // for callout: explicit visibility modifiers
    public char[]           name;

	public InferenceKind inferred = InferenceKind.NONE;

    // currently only use: TagBits.AnnotationResolved
	public long tagBits = 0L;

	// link to the original if copy-inherited:
	public CallinCalloutBinding copyInheritanceSrc;
//{OTDyn: management of callin ids across team inheritance:
	public int callinIdMax = -1;
// SH}

    //@param CALLOUT
    public CallinCalloutBinding(boolean 		 isCalloutOverride,
       							MethodBinding    roleMethodBinding,
						        ReferenceBinding declaringRoleClass,
    						    int              calloutModifier,
    						    int 			 declaredModifiers)
    {
        this._declaringRoleClass = declaringRoleClass;
        this._roleMethodBinding  = roleMethodBinding;
        if(isCalloutOverride){
			this.type            = CALLOUT_OVERRIDE;
        } else {
        	this.type            = CALLOUT;
        }
        this.calloutModifier     = calloutModifier;
        this.declaredModifiers   = declaredModifiers;
    }

    //@param type CALLIN
    public CallinCalloutBinding(
            ReferenceBinding declaringRoleClass, CallinMappingDeclaration mappingDecl)
    {
        this.type                    = CALLIN;
        this._declaringRoleClass     = declaringRoleClass;
    	this.name                    = mappingDecl.name;
        this.callinModifier          = mappingDecl.callinModifier;
        this._roleMethodBinding      = mappingDecl.getRoleMethod();
        //this._baseMethods            = null; // set during resolve of mapping declaration
        // but beware: not in all cases this resolve is called at all (e.g., no baseclass declared).
    }

    /**
	 * @param declaringRole
	 * @param name
	 * @param callinModifier
	 */
	public CallinCalloutBinding(ReferenceBinding declaringRole, char[] name, int callinModifier) {
        this.type                    = CALLIN;
		this._declaringRoleClass     = declaringRole;
		this.name = name;
        this.callinModifier          = callinModifier;
	}

	/**
	 * Create an unresolved callin binding.
     * (details to be filled by by CallinMethodMappingsAttribute.merge->createBinding())
	 *
	 * @param declaringRole
	 * @param name
	 */
	public CallinCalloutBinding(ReferenceBinding declaringRole, char[] name) {
        this.type                    = CALLIN;
		this._declaringRoleClass     = declaringRole;
		this.name = name;
	}

	/* (non-Javadoc)
     * @see org.eclipse.jdt.internal.compiler.lookup.Binding#bindingType()
     */
    @Override
	public int kind()
    {
        return BINDING;
    }

    public CallinCalloutBinding getOrigin() {
    	if (this.copyInheritanceSrc != null)
    		return this.copyInheritanceSrc.getOrigin();
    	return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.jdt.internal.compiler.lookup.Binding#readableName()
     */
    @Override
	public char[] readableName()
    {
        StringBuffer buffer = new StringBuffer();
        buffer.append(toString());

        return buffer.toString().toCharArray();
    }

    /**
     * @return true if Binding is CallinBinding
     */
    public boolean isCallin()
    {
        return this.type == CALLIN;
    }

    public boolean isReplaceCallin() {
    	return this.type == CALLIN && this.callinModifier == REPLACE;
    }
	/**
	 * @return true if Binding is CalloutOverrideBinding
	 */
	private boolean isCalloutOverride()
	{
		return this.type == CALLOUT_OVERRIDE;
	}
    /**
     * @return true if Binding is Callout or CalloutOverride
     */
    public boolean isCallout()
    {

        return this.type == CALLOUT || this.type == CALLOUT_OVERRIDE;
    }

    public boolean hasValidBaseMethods() {
    	if (   this._baseMethods == null
			|| this._baseMethods.length == 0)
    		return false;
    	for (int i = 0; i < this._baseMethods.length; i++) {
    		if (!this._baseMethods[i].isValidBinding())
    			return false;
		}
    	return true;
    }

    public boolean hasValidRoleMethod() {
    	return
			   this._roleMethodBinding != null
			&& this._roleMethodBinding.isValidBinding();
    }

    /** Get the problemId of the first erroneous method binding or NoError. */
    @Override
	public int problemId() {
    	if (this._roleMethodBinding != null && !this._roleMethodBinding.isValidBinding())
    		return this._roleMethodBinding.problemId();
    	if (this._baseMethods != null)
    		for (MethodBinding method : this._baseMethods)
    			if (!method.isValidBinding())
    				return method.problemId();
    	if (this._baseField != null)
    		return this._baseField.problemId();
    	return ProblemReasons.NoError;
    }

    public boolean isRoleMethodOverriddenIn(ReferenceBinding subRole) {
    	if (!hasValidRoleMethod()) return false;
    	for (MethodBinding subMethod : subRole.getMethods(this._roleMethodBinding.selector)) {
			if (TypeAnalyzer.isEqualMethodSignature(
								this._roleMethodBinding.declaringClass.enclosingType(), this._roleMethodBinding,
								subRole.enclosingType(), subMethod,
								TypeAnalyzer.ANY_MATCH))
				return true;
		}
    	return false;
    }

    // copied from MethodBinding.getTypeVariable()
	public TypeVariableBinding getTypeVariable(char[] variableName) {
		for (int i = this.typeVariables.length; --i >= 0;)
			if (CharOperation.equals(this.typeVariables[i].sourceName, variableName))
				return this.typeVariables[i];
		return null;
	}

    @Override
	@SuppressWarnings("nls")
	public char[] computeUniqueKey(boolean isLeaf) {
    	// callin mappings have a name:
    	if (this.name != null)
    		return this.name;

    	// fail safe for erroneous bindings (see CallinCalloutScope.createBinding())
    	if (this._roleMethodBinding == null)
    		return new char[0]; // like to call new String() on the result, => must not be null!

    	// callout mappings assemble a key from elements:
    	if (this._baseMethods != Binding.NO_METHODS)
    		return CharOperation.concat(
    				this._roleMethodBinding.computeUniqueKey(isLeaf),
    				"->".toCharArray(),
    				this._baseMethods[0].computeUniqueKey(isLeaf));

    	// fail safe for callout without base methods:
		return CharOperation.concat(
				this._roleMethodBinding.computeUniqueKey(isLeaf),
				"->".toCharArray());
    }

    @Override
	public void setAnnotations(AnnotationBinding[] annotations, boolean forceStore) {
    	this._declaringRoleClass.storeAnnotations(this, annotations, forceStore);
    }

    @Override
	public AnnotationBinding[] getAnnotations() {
      return this._declaringRoleClass.retrieveAnnotations(this);
    }


    @Override
	@SuppressWarnings("nls")
	public String toString()
    {
        String result = (this.name == null) ? "" : new String(this.name)+": ";

        result +=
            (this._roleMethodBinding != null)
            	? this._roleMethodBinding.toString(false) // no modifiers
                : "NULL ROLE METHODS";

        if (isCallin())
        {
            result += "<- "+CallinMappingDeclaration.callinModifier(this.callinModifier)+" ";
        }
        else if(isCallout())
        {
        	if(isCalloutOverride())
				result += "=> ";
        	else
            	result += "-> ";
        }
        if (this._baseMethods != null)
        	for (int i = 0; i < this._baseMethods.length; i++) {
				result += this._baseMethods[i].toString(false); // no modifiers
				if (i<this._baseMethods.length-1)
					result += ", ";
			}

        result += ';';

        return result;
    }

	/** Answer the name of this callin qualified with the declaring class's name. */
	public char[] getQualifiedName() {
		char[] theName = this.name;
		if (theName[0] == '<')
			return theName; // synthetic name is already unique.
		ReferenceBinding currentType = this._declaringRoleClass;
		return CharOperation.concat(theName, currentType.readableName(), '$');
	}
	/**
	 * Answer the name of the role that introduced this callin mapping 
	 * (support for overriding in otredyn).
	 */
	public ReferenceBinding introducingRoleClass() {
		ReferenceBinding declaringRole = this._declaringRoleClass;
		if (this.name == null)
			return declaringRole;
		if (this.name[0] != '<') {
			ReferenceBinding currentRole = declaringRole;
			while (currentRole != null && currentRole.isRole()) {
				for (CallinCalloutBinding mapping : currentRole.callinCallouts) {
					if (CharOperation.equals(this.name, mapping.name)) {
						declaringRole = currentRole;
					}
				}
				currentRole = currentRole.superclass();
			}
		}
		return declaringRole;
	}
}
