package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.objectteams.otredyn.runtime.IBinding;
import org.eclipse.objectteams.otredyn.runtime.TeamManager;
import org.objectteams.ITeam;

public class CallSiteContext {
	
	public static Map<String, CallSiteContext> contexts = new ConcurrentHashMap<>();
	
	public ITeam[] teams;
	public int index;
	public final int bmId;
	public final String joinpointDescr;
	
	public final Class<?> baseClass;
	
	public HashSet<IBinding> proccessedBindings = new HashSet<IBinding>();

	public CallSiteContext(String joinpointDescr, int bmId, Class<?> baseClass) {
		this.joinpointDescr = joinpointDescr;
		this.bmId = bmId;
		this.index = 0;
		this.baseClass = baseClass;
		
		final int joinpointId = TeamManager.getJoinpointId(joinpointDescr);
		teams = TeamManager.getTeams(joinpointId);
	}

}
