package org.eclipse.objectteams.otredyn.runtime.dynamic;

import org.objectteams.IBoundBase2;
import org.objectteams.ITeam;

public class CallSiteContext {

	private IBoundBase2 baseArg;
	private ITeam[] teams;
	private int index;
	private int[] callinIs;
	private int bmId;
	private Object[] args;
	private Object[] boxedArgs;

	public CallSiteContext(IBoundBase2 baseArg, ITeam[] teams, int index, int[] callinIs, int bmId, Object[] args,
			Object[] boxedArgs) {
		this.baseArg = baseArg;
		this.teams = teams;
		this.index = index;
		this.callinIs = callinIs;
		this.bmId = bmId;
		this.args = args;
		this.boxedArgs = boxedArgs;
	}

}
