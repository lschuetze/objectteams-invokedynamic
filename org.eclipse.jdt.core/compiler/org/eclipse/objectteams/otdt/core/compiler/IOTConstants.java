/**********************************************************************
 * This file is part of "Object Teams Development Tooling"-Software
 *
 * Copyright 2003, 2014 Fraunhofer Gesellschaft, Munich, Germany,
 * for its Fraunhofer Institute for Computer Architecture and Software
 * Technology (FIRST), Berlin, Germany and Technical University Berlin,
 * Germany.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 *
 * Contributors:
 * Fraunhofer FIRST - Initial API and implementation
 * Technical University Berlin - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otdt.core.compiler;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;

/**
 * @author stephan
 * @noimplement This interface is not intended to be implemented by clients.
 */
@SuppressWarnings("nls")
public interface IOTConstants
{
	/**
	 * @since 3.7
	 */
	public final static class OTVersion {
		// methods, not constants, so these won't get inlined in any byte code.
		public static final int  getMajor() { return 1; }
		public static final int  getMinor() { return 7; }
		public static final int  getRevsion() { return 0; }
		public static final int  getCompilerVersionMin() { return (1<<9)+(4<<5)+1; }// byte code incompatibility introduced in internal version 1.4.1 (OTDT 1.4.0M3)
	}

    // These keywords are allowed in std-Java files (even if compiled by the OTDT):
    // (not mentioning situational keywords like replace ...).
	public static final char[][] OT_KEYWORDS = new char[][] {
    	"as".toCharArray(),
		"base".toCharArray(),
		"callin".toCharArray(),
		"playedBy".toCharArray(),
		"precedence".toCharArray(),
		"tsuper".toCharArray(),
		"with".toCharArray(),
		"when".toCharArray()
    };

    // once this was a keyword, now is a special identifier,
    // bound by being a
    // + parameter of base predicates
    // + parameter of callin wrapper methods (for use in right hand side of parameter mapping)
    // + local variable in callout wrappers (for use in right hand side of parameter mapping)
	public static final char[] BASE = "base".toCharArray();


    // Modifiers for synthetic role interfaces:
    public static final int AccSynthIfc = ClassFileConstants.AccInterface | ClassFileConstants.AccSynthetic;

    // General prefixes etc:
    public static final String JAVA_SEPARATOR = "$";

	public static final String OT_SEPARATOR		  = "_";
    public static final String OT_DELIM           = "__OT__";
	public static final char[] OT_DELIM_NAME      = OT_DELIM.toCharArray();
	public static final int    OT_DELIM_LEN       = OT_DELIM.length();

	public static final String OT_DOLLAR          = "_OT$";
	public static final char[] OT_DOLLAR_NAME      = OT_DOLLAR.toCharArray();
	public static final int    OT_DOLLAR_LEN       = OT_DOLLAR.length();


	// Tsuper marking:
	public static final String TSUPER_OT           = "TSuper" + OT_DELIM;
	public static final char[] TSUPER_OT_NAME      = TSUPER_OT.toCharArray();
	public static final int    TSUPER_OT_LEN       = TSUPER_OT.length();

	public static final char[] MARKER_ARG_NAME     = (OT_DOLLAR + "marker").toCharArray();

	// Role file cache type:
	public static final char[] ROFI_CACHE          = ("RoFi" + OT_DELIM).toCharArray();

    public static final char[] TTHIS               = "tthis".toCharArray();

    public static final char[] CREATOR_PREFIX_NAME = (OT_DOLLAR+"create$").toCharArray();

	public static final char[] OT_GETFIELD         = (OT_DOLLAR+"get$").toCharArray();
	public static final char[] OT_SETFIELD         = (OT_DOLLAR+"set$").toCharArray();
	public static final String OT_DOLLAR_ARG       = OT_DOLLAR + "arg";

    // bytecode attributes:
    public static final String PLAYEDBY               = "PlayedBy";
    public static final char[] PLAYEDBY_NAME          = PLAYEDBY.toCharArray();
    public static final char[] ROLE_BASE_BINDINGS     = "CallinRoleBaseBindings".toCharArray();
    public static final char[] BOUND_CLASSES_HIERARCHY= "BoundClassesHierarchy".toCharArray();
	public static final char[] ROLE_FILES             = "RoleFiles".toCharArray();
    public static final char[] MODIFIERS_NAME         = "Modifiers".toCharArray();
    public static final char[] ROLECLASS_METHOD_MODIFIERS_NAME
												      = "RoleClassMethodModifiers".toCharArray();
    public static final char[] REFERENCED_TEAMS       = "ReferencedTeams".toCharArray();
    public static final char[] INHERITED_ROLES        = "InheritedRoles".toCharArray();
    public static final char[] CALLOUT_MAPPINGS       = "CalloutMappings".toCharArray();
    public static final char[] CALLIN_METHOD_MAPPINGS = "CallinMethodMappings".toCharArray();
    public static final char[] STATIC_REPLACE_BINDINGS= "StaticReplaceBindings".toCharArray();
    public static final char[] TYPE_ANCHOR_LIST       = "TypeAnchorList".toCharArray();
    public static final char[] FIELD_TYPE_ANCHOR      = "FieldTypeAnchor".toCharArray();
    public static final char[] ANCHOR_USAGE_RANKS     = "AnchorUsageRanks".toCharArray();
    public static final char[] COPY_INHERITANCE_SOURCE_NAME = "CopyInheritanceSrc".toCharArray();
      // handling of nested types within roles:
    public static final char[] ROLE_LOCAL_TYPES       = "RoleLocalTypes".toCharArray();
    public static final char[] CALLIN_PRECEDENCE      = "CallinPrecedence".toCharArray();
    public static final char[] CALLIN_PARAM_MAPPINGS  = "CallinParamMappings".toCharArray();
    public static final char[] OT_CLASS_FLAGS         = "OTClassFlags".toCharArray();
      // SourceDebugExtension for JSR-045:
    public static final char[] SOURCE_DEBUG_EXTENSION = "SourceDebugExtension".toCharArray();
      // possible values for OT_CLASS_FLAGS (or-able):
    public static final int    OT_CLASS_TEAM            = 1;
    public static final int    OT_CLASS_ROLE            = 2;
    public static final int    OT_CLASS_ROLE_LOCAL      = 4;
    public static final int    OT_CLASS_PURELY_COPIED   = 8;
    public static final int    OT_CLASS_ROLE_FILE       = 16;
    public static final int    OT_CLASS_FLAG_HAS_TSUPER = 32;
    public static final int    OT_CLASS_CONFINED        = 64; // means: superclass Object should be removed on loading

    public static final char[] CALLIN_FLAGS           = "CallinFlags".toCharArray();
      // possible values for CALLIN_FLAGS:
    public static final int    CALLIN_FLAG_OVERRIDING = 1;
    public static final int    CALLIN_FLAG_WRAPPER    = 2;
    public static final int    CALLIN_FLAG_DEFINITELY_MISSING_BASECALL  = 8;
    public static final int    CALLIN_FLAG_POTENTIALLY_MISSING_BASECALL = 16;
    public static final int    CALLIN_FLAG_BASE_SUPER_CALL              = 32;
    public static final int    CALLIN_RETURN_VOID    = 1 << 8;
    public static final int    CALLIN_RETURN_BOOLEAN = 2 << 8;
    public static final int    CALLIN_RETURN_BYTE    = 3 << 8;
    public static final int    CALLIN_RETURN_CHAR    = 4 << 8;
    public static final int    CALLIN_RETURN_SHORT   = 5 << 8;
    public static final int    CALLIN_RETURN_DOUBLE  = 6 << 8;
    public static final int    CALLIN_RETURN_FLOAT   = 7 << 8;
    public static final int    CALLIN_RETURN_INT     = 8 << 8;
    public static final int    CALLIN_RETURN_LONG    = 9 << 8;
	public static final int    CALLIN_RETURN_MASK    = 0x0F00;
	  //
    public static final char[] CLASS_INFO_ANCHORS = "ClassInfoAnchors".toCharArray();

    public static final char[] OT_COMPILER_VERSION = "OTCompilerVersion".toCharArray();

	public static final char[] CALLS_BASE_CTOR     = "CallsBaseConstructor".toCharArray();

	public static final char[] JOINPOINTS          = "OTJoinPoints".toCharArray();

	public static final char[] OTSPECIAL_ACCESS    = "OTSpecialAccess".toCharArray();


	public static final char[] ROLE = "role".toCharArray();
	public static final char[] _OT_TAG = (OT_DOLLAR + "Tag").toCharArray();
	public static final char[] _OT_BASE  	= (OT_DOLLAR + "base").toCharArray();
    public static final char[] _OT_GETBASE  = (OT_DOLLAR + "getBase").toCharArray();
	public static final char[] _OT_BASE_ARG	= (OT_DOLLAR + "base_arg").toCharArray();
	public static final char[] _OT_ROLE_ARG	= (OT_DOLLAR + "role_arg").toCharArray();
	// name of ILowerable.lower()
	public static final char[] LOWER        = "lower".toCharArray();
	public static final char[] ILOWERABLE    = "ILowerable".toCharArray();
	public static final char[] _OT_GETTEAM  = (OT_DOLLAR + "getTeam").toCharArray();
	public static final char[] GET_TEAM_SIGNATURE = "()Lorg/objectteams/ITeam;".toCharArray();

	public static final char[] _OT_LIFT_TO = (OT_DOLLAR + "liftTo$").toCharArray();
	public static final char[] CAST_PREFIX = (OT_DOLLAR + "castTo$").toCharArray();
	public static final char[] GET_CLASS_PREFIX = (OT_DOLLAR + "getClass$").toCharArray();

	// prevent/aid garbage collection (see OTRE: org.eclipse.objectteams.otre.OTConstants)
	public static final char[] ADD_ROLE       = (OT_DOLLAR + "addRole").toCharArray();
	public static final char[] REMOVE_ROLE    = (OT_DOLLAR + "removeRole").toCharArray();

	// OTDYN uses just one method for both:
	public static final char[] ADD_REMOVE_ROLE = (OT_DOLLAR + "addOrRemoveRole").toCharArray();
	
// predefined classes and interfaces:
	public static final char[] ORG = "org".toCharArray();
	public static final char[] OBJECTTEAMS = "objectteams".toCharArray();
	
	public static final char[][] ORG_OBJECTTEAMS = {ORG, OBJECTTEAMS};
	public static final char[] ITEAM = "ITeam".toCharArray();
	public static final char[] TEAM = "Team".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_ITEAM = {ORG, OBJECTTEAMS, ITEAM};
	public static final char[][] ORG_OBJECTTEAMS_TEAM = {ORG, OBJECTTEAMS, TEAM};
	public static final char[] STR_ORG_OBJECTTEAMS_TEAM = ("org.objectteams.Team").toCharArray();
	public static final char[] STR_ORG_OBJECTTEAMS_ITEAM = ("org.objectteams.ITeam").toCharArray();

	public static final char[] ICONFINED = "IConfined".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_ICONFINED = {ORG, OBJECTTEAMS, ICONFINED};

	public static final char[] TEAM_ICONFINED = "ITeam$IConfined".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_ITEAM_ICONFINED = {ORG, OBJECTTEAMS, TEAM_ICONFINED};

	public static final char[] OTCONFINED = "__OT__Confined".toCharArray();
	public static final char[] CONFINED = "Confined".toCharArray();
	public static final char[] TEAM_CONFINED = "Team$Confined".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_TEAM_CONFINED = {ORG, OBJECTTEAMS, TEAM_CONFINED};

	public static final char[] TEAM_OTCONFINED = "Team$__OT__Confined".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_TEAM_OTCONFINED = {ORG, OBJECTTEAMS, TEAM_OTCONFINED};

	public static final char[] LIFTING_FAILED_EXCEPTION = "LiftingFailedException".toCharArray();
	public static final char[][] O_O_LIFTING_FAILED_EXCEPTION = {ORG, OBJECTTEAMS, LIFTING_FAILED_EXCEPTION};
	public static final char[][] SOFT_LIFTING_FAILED_EXCEPTION = {ORG, OBJECTTEAMS, "SoftLiftingFailedException".toCharArray()};
	public static final char[] WRONG_ROLE_EXCEPTION = "WrongRoleException".toCharArray();
	public static final char[] DUPLICATE_ROLE_EXCEPTION = "DuplicateRoleException".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_DUPLICATE_ROLE = {ORG, OBJECTTEAMS, DUPLICATE_ROLE_EXCEPTION};
	public static final char[] LIFTING_VETO_EXCEPTION = "LiftingVetoException".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_LIFTING_VETO = {ORG, OBJECTTEAMS, LIFTING_VETO_EXCEPTION};
	public static final char[] RESULT_NOT_PROVIDED_EXCEPTION = "ResultNotProvidedException".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_RESULT_NOT_PROVIDED = {ORG, OBJECTTEAMS, RESULT_NOT_PROVIDED_EXCEPTION};
	public static final char[] IBOUNDBASE                        = "IBoundBase".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_IBOUNDBASE  = {ORG, OBJECTTEAMS, IBOUNDBASE};
	/**
	 * @since 3.10 OT 2.3 for OTDRE
	 */
	public static final char[] IBOUNDBASE2                        = "IBoundBase2".toCharArray();
	/**
	 * @since 3.10 OT 2.3 for OTDRE
	 */
	public static final char[][] ORG_OBJECTTEAMS_IBOUNDBASE2  = {ORG, OBJECTTEAMS, IBOUNDBASE2};

	public static final char[][] ROLE_CAST_EXCEPTION 			  = {ORG, OBJECTTEAMS,
																	 "RoleCastException".toCharArray()};
	
	public static final char[][] MH_LOOKUP = CharOperation.splitOn('.', "java.lang.invoke.MethodHandles.Lookup".toCharArray());
	public static final char[][] STRING = CharOperation.splitOn('.', "java.lang.String".toCharArray());
	public static final char[][] INVOKE_METHODTYPE = CharOperation.splitOn('.', "java.lang.invoke.MethodType".toCharArray());
	
	/** 
	 * @since 3.7 (OT 2.0)
	 */
	public static final char[] INSTANTIATION = "Instantiation".toCharArray();
	/** 
	 * @since 3.7 (OT 2.0)
	 */
	public static final char[][] ORG_OBJECTTEAMS_INSTANTIATION = {ORG, OBJECTTEAMS,
		 															 INSTANTIATION};
	
	public static final char[][] ILLEGAL_ROLE_CREATION_EXCEPTION = {ORG, OBJECTTEAMS,
		 															 "IllegalRoleCreationException".toCharArray()};
	public static final char[][] ORG_OBJECTTEAMS_ITEAMMIGRATABLE = {ORG, OBJECTTEAMS,
																	 "ITeamMigratable".toCharArray()};
	public static final char[] MIGRATE_TO_TEAM = "migrateToTeam".toCharArray();
	public static final char[][] ORG_OBJECTTEAMS_IBASEMIGRATABLE = {ORG, OBJECTTEAMS,
																	 "IBaseMigratable".toCharArray()};
	public static final char[] MIGRATE_TO_BASE = "migrateToBase".toCharArray();

	public static final char[][] OTRE_INTERNAL_ERROR = new char[][]{"org".toCharArray(), 
																	"objectteams".toCharArray(), 
																	"OTREInternalError".toCharArray()};
	/**
	 * @since 3.9 (OT 2.2)
	 */
	public static final char[][] SNEAKY_EXCEPTION = new char[][]{ORG, OBJECTTEAMS,
																	"SneakyException".toCharArray()};


	// Type IDs (cf. type TypeIds)
	public static final int T_OrgObjectTeamsITeam      = 100;
	public static final int T_OrgObjectTeamsTeam       = 101;
	public static final int T_OrgObjectTeamsIBoundBase = 102;
	/**
	 * @since 3.7 (OT 2.0)
	 */
	public static final int T_OrgObjectTeamsInstantiation = 103;
	/**
	 * @since 3.10 (OT 2.3)
	 */
	public static final int T_OrgObjectTeamsIBoundBase2 = 104;

    // special identifiers:
	public static final char[] RESULT = "result".toCharArray();
	public static final char[] OT_RESULT = (OT_DOLLAR+"result").toCharArray();
    public static final char[] NAME_REPLACE = "replace".toCharArray();
    public static final char[] NAME_BEFORE  = "before".toCharArray();
    public static final char[] NAME_AFTER   = "after".toCharArray();
	// elements for generated method body:
	public static final char[] LENGTH = "length".toCharArray();
    public static final char[][] WEAK_HASH_MAP = {
        "org".toCharArray(), "objectteams".toCharArray(), "DoublyWeakHashMap".toCharArray()
    };
    public static final char[][] COLLECTION = {
        "java".toCharArray(), "util".toCharArray(), "Collection".toCharArray()
    };
    public static final char[][] ARRAY_LIST = {
        "java".toCharArray(), "util".toCharArray(), "ArrayList".toCharArray()
    };
    public static final char[] CACHE_PREFIX = (OT_DOLLAR+"cache"+OT_DOLLAR).toCharArray();
	public static final char[] CACHE_INIT_TRIGGERER = "_OT$cacheInitTrigger".toCharArray();
	public static final char[] OT_INIT_CACHES = "_OT$initCaches".toCharArray();

	public static final char[] GET = "get".toCharArray();
	public static final char[] PUT = "put".toCharArray();
	public static final char[] CONTAINS_KEY = "containsKey".toCharArray();
	public static final char[] ACTIVATION_LEVEL = "activationLevel".toCharArray();
	public static final char[] MY_ROLE = "myRole".toCharArray();
	public static final char[] _CLASS_CAST_EXCEPTION_ = "ClassCastException".toCharArray();
	public static final char[] CLASS_CAST_EXCEPTION = "classCastException".toCharArray();
	public static final char[] OT_TRANSFORM_ARRAY = (OT_DOLLAR+"transformArray").toCharArray();

	// internal API function of o.o.Team:
	public static final char[] SET_EXECUTING_CALLIN = "_OT$setExecutingCallin".toCharArray();
	// predicates
	public static final char[] PREDICATE_METHOD_NAME = "_OT$when".toCharArray();
	public static final char[] BASE_PREDICATE_PREFIX = "_OT$base_when".toCharArray();
	public static final char[] QUERY_MODULE_SUFFIX = "_Queries__OT__".toCharArray();
	// decapsulation accessor prefix:
	public static final char[] OT_DECAPS = "_OT$decaps$".toCharArray();
	
	// --------- for MethodBinding.otBits: --------------------------
	/**
	 * have types been wrapped in the signature?
	 * @since 3.13
	 */
	int HasWrappedSignature = 0x0001;
	/**
	 * @since 3.13
	 */
	int IsCopyOfParameterized = 0x0002;

	// --------- for VariableBinding.otBits: --------------------------
	/**
	 * is a _OT$base field faked to compensate for weakening?
	 * @since 3.13
	 */
    int IsFakedField = 0x0001;								// for field binding
	/**
     * "new SomeTeam()" expression, unmatchable anchor
	 * @since 3.13
	 */
    int IsFreshTeamInstance = 0x0002;		 				// for LocalVariableBinding
	/**
	 * is a role field needing a cast to the role class:
	 * @since 3.13
	 */
    int IsRoleClassField = 0x0004;							// for field binding (role)
}
