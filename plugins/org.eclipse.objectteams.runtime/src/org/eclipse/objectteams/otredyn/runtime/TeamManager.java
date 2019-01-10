/**********************************************************************
 * This file is part of "Object Teams Dynamic Runtime Environment"
 * 
 * Copyright 2009, 2015 Oliver Frank and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Please visit http://www.eclipse.org/objectteams for updates and contact.
 * 
 * Contributors:
 *		Oliver Frank - Initial API and implementation
 *		Stephan Herrmann - Initial API and implementation
 **********************************************************************/
package org.eclipse.objectteams.otredyn.runtime;

import java.lang.invoke.SwitchPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.objectteams.ITeam;
import org.objectteams.ITeamManager;
import org.objectteams.Team;

/**
 * This class administrates the the active teams and their callin ids for all
 * joinpoints. Teams have to register/unregister themselves at this class, while
 * their activation/deactivation. The generated client code can call methods of
 * this class to get the active teams and callin ids.
 * 
 * @author Oliver Frank
 *
 */
public class TeamManager implements ITeamManager {

	// void handleTeamStateChange(ITeam t, TeamStateChange stateChange) ;
	private static List<List<ITeam>> _teams = new ArrayList<List<ITeam>>();
	private static List<List<Integer>> _callinIds = new ArrayList<List<Integer>>();
	private static List<List<SwitchPoint>> _switchpoints = new ArrayList<>();
	private static Map<String, Integer> joinpointMap = new HashMap<String, Integer>();
	// key: Team class, value: list of global memberIds, indexed by local accessId,
	// id of null means "not mapped in this team (try super)"
	private static Map<Class<?>, List<Integer>> accessIdMap = new HashMap<Class<?>, List<Integer>>();
	private static int currentJoinpointId = 0;
	// map all original joinpoints to their inherited versions in subclasses
	private static Map<Integer, List<Integer>> joinpointToSubJoinpoints = new HashMap<Integer, List<Integer>>();
	private static IClassRepository classRepository;

	// synchronization: fields _teams and _callinIds are protected using the
	// TeamManager class object as the monitor

	public static void setup(IClassRepository repo) {
		classRepository = repo;
		Team.registerTeamManager(new TeamManager()); // install callback
	}

	public synchronized static ITeam[] getTeams(int joinpointId) {
		List<ITeam> teams = _teams.get(joinpointId);
		return teams.toArray(new ITeam[teams.size()]);
	}

	public synchronized static int[] getCallinIds(int joinpointId) {
		List<Integer> ids = _callinIds.get(joinpointId);
		return ids.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * Returns all active teams and corresponding callin IDs for joinpoint. This
	 * method is intended to be called by generated client code.
	 * 
	 * @param joinpointId
	 * @return a two-element array or null if there are no active teams. If non-null
	 *         the first array element is an array (ITeam[]) of all active teams for
	 *         the joinpoint, and the second array element is an array (int[]) of
	 *         corresponding callind IDs. Both arrays have the same length and
	 *         elements with equal index correspond between both sub-arrays.
	 */
	public synchronized static Object[] getTeamsAndCallinIds(int joinpointId) {
		List<ITeam> teams = _teams.get(joinpointId);
		int size = teams.size();
		if (size == 0)
			return null;
		List<Integer> callinIds = _callinIds.get(joinpointId);
		ITeam[] active = new ITeam[size];
		int[] ids = new int[size];
		int count = 0;
		Thread th = Thread.currentThread();
		for (int i = 0; i < active.length; i++) {
			ITeam t = teams.get(i);
			if (t.isActive(th)) {
				active[count] = t;
				ids[count++] = callinIds.get(i);
			}
		}
		if (count == 0)
			return null;
		if (count != size) {
			System.arraycopy(active, 0, active = new ITeam[count], 0, count);
			System.arraycopy(ids, 0, ids = new int[count], 0, count);
		}
		return new Object[] { active, ids };
	}

	/**
	 * Returns the member id for a given team and a access id used in this team.
	 * This method is intended to be called by generated client code.
	 * 
	 * @param accessId
	 * @param Team
	 * @return
	 */
	public static int getMemberId(int accessId, Class<? extends ITeam> teamClass) {
		List<Integer> teamMap = accessIdMap.get(teamClass);
		Integer id = -1;
		if (teamMap == null || accessId >= teamMap.size() || (id = teamMap.get(accessId)) == null) {
			Class<?> superClass = teamClass.getSuperclass();
			if (ITeam.class.isAssignableFrom(superClass)) {
				@SuppressWarnings("unchecked")
				Class<? extends ITeam> superTeam = (Class<? extends ITeam>) superClass;
				return getMemberId(accessId, superTeam);
			}
			if (id == null)
				return -1;
		}
		return id;
	}

	/**
	 * Returns and possibly creates a globally unique id for a joinpoint identifier.
	 * Additionally it prepares the structures needed to store the teams and callin
	 * ids for a joinpoint.
	 * 
	 * @param joinpointIdentifier
	 * @return a joinpoint id
	 */
	public synchronized static int getJoinpointId(String joinpointIdentifier) {
		Integer joinpointId = getExistingJoinpointId(joinpointIdentifier);
		if (joinpointId == null) {
			joinpointMap.put(joinpointIdentifier, currentJoinpointId);
			List<ITeam> teams = new ArrayList<ITeam>();
			_teams.add(teams);
			List<Integer> callinIds = new ArrayList<Integer>();
			_callinIds.add(callinIds);
			return currentJoinpointId++;
		}
		return joinpointId;
	}

	/**
	 * Returns an existing globally unique joinpoint id for a joinpoint identifier
	 * 
	 * @param joinpointIdentifier
	 * @return the joinpoint id or null if a joinpoint id doesn't exist for this
	 *         joinpoint identifier
	 */
	private static Integer getExistingJoinpointId(String joinpointIdentifier) {
		return joinpointMap.get(joinpointIdentifier);
	}

	/**
	 * Handles registration and unregistration of teams. Stores the team and the ids
	 * dependend on the joinpoints and tell the {@link AbstractBoundClass}
	 * representing the base classes of the team, that there is new Binding
	 * 
	 * @param t
	 * @param stateChange
	 */
	public void handleTeamStateChange(ITeam t, ITeamManager.TeamStateChange stateChange) {
		IClassIdentifierProvider provider = ClassIdentifierProviderFactory.getClassIdentifierProvider();
		Class<? extends ITeam> teamClass = t.getClass();
		String teamId = provider.getClassIdentifier(teamClass);
		IBoundTeam teem = classRepository.getTeam(teamClass.getName(), teamId, teamClass.getClassLoader());

		for (IBinding binding : teem.getBindings()) {
			// OTDRE cannot add methods into a sub base, hence we have to use the declaring
			// base class for static methods:
			// (see https://bugs.eclipse.org/435136#c1)
			String boundClassName = ((binding.getBaseFlags() & IBinding.STATIC_BASE) != 0)
					? binding.getDeclaringBaseClassName()
					: binding.getBoundClass();
			String boundClassIdentifier = provider.getBoundClassIdentifier(teamClass, boundClassName);
			// FIXME(SH): the following may need adaptation for OT/Equinox or other
			// multi-classloader settings:
			IBoundClass boundClass = classRepository.getBoundClass(boundClassName.replace('/', '.'),
					boundClassIdentifier, teamClass.getClassLoader());
			switch (binding.getType()) {
			case CALLIN_BINDING:
				handleBindingForBase(t, stateChange, binding, boundClass, provider);
				break;
			default:
				// no further action for *ACCESS bindings
			}
		}
	}

	private void handleBindingForBase(ITeam t, ITeamManager.TeamStateChange stateChange, IBinding binding,
			IBoundClass boundClass, IClassIdentifierProvider provider) {
		IMethod method = boundClass.getMethod(binding.getMemberName(), binding.getMemberSignature(),
				binding.getBaseFlags(), binding.isHandleCovariantReturn());
		int joinpointId = getJoinpointId(boundClass.getMethodIdentifier(method));
		synchronized (method) {
			stateChangeForJoinpoint(t, stateChange, binding, boundClass, method, joinpointId);
		}
		boundClass.handleAddingOfBinding(binding); // TODO: do we want/need to group all bindings into one action?

		for (IBoundClass tsubBase : boundClass.getTSubsOfThis(classRepository, provider)) {
			handleBindingForBase(t, stateChange, binding, tsubBase, provider);
		}
	}

	private void stateChangeForJoinpoint(ITeam t, ITeamManager.TeamStateChange stateChange, IBinding binding,
			IBoundClass boundClass, IMethod method, int joinpointId) {
		Set<Integer> joinpointIds = new HashSet<Integer>();
		collectSubJoinpoints(joinpointId, joinpointIds);
		for (Integer id : joinpointIds) {
			changeTeamsForJoinpoint(t, binding.getPerTeamId(), id, stateChange);
		}
	}

	private void collectSubJoinpoints(int joinpointId, Set<Integer> joinpointIds) {
		joinpointIds.add(joinpointId);
		List<Integer> subJoinpoints = joinpointToSubJoinpoints.get(joinpointId);
		if (subJoinpoints != null)
			for (Integer subJoinpoint : subJoinpoints)
				collectSubJoinpoints(subJoinpoint, joinpointIds);
	}

	/**
	 * When a team is about to be activated propagate its bindings to all base
	 * classes, but don't yet register any join points, we aren't activating yet.
	 */
	public static void prepareTeamActivation(Class<? extends ITeam> teamClass) {
		String teamName = teamClass.getName();
		ClassLoader teamClassLoader = teamClass.getClassLoader();
		IClassIdentifierProvider provider = ClassIdentifierProviderFactory.getClassIdentifierProvider();
		String teamId = provider.getClassIdentifier(teamClass);
		IBoundTeam teem = classRepository.getTeam(teamName, teamId, teamClassLoader);

		for (IBinding binding : teem.getBindings()) {
			String boundClassName = binding.getBoundClass();
			String boundClassIdentifier = provider.getBoundClassIdentifier(teamClass, boundClassName);
			// FIXME(SH): the following may need adaptation for OT/Equinox or other
			// multi-classloader settings:
			IBoundClass boundClass = classRepository.getBoundClass(boundClassName.replace('/', '.'),
					boundClassIdentifier, teamClass.getClassLoader());
			switch (binding.getType()) {
			case CALLIN_BINDING:
				prepareBindingForBase(binding, boundClass, provider);
				break;
			default: // no further action for *ACCESS bindings
			}
		}
	}

	private static void prepareBindingForBase(IBinding binding, IBoundClass boundClass,
			IClassIdentifierProvider idProvider) {
		boundClass.handleAddingOfBinding(binding);
		for (IBoundClass tsubBase : boundClass.getTSubsOfThis(classRepository, idProvider))
			prepareBindingForBase(binding, tsubBase, idProvider);
	}

	public static void handleTeamLoaded(Class<? extends ITeam> teamClass) {
		if (teamClass != null)
			handleDecapsulation(teamClass);
		performPendingTask();
	}

	private static void handleDecapsulation(Class<? extends ITeam> teamClass) {
		IClassIdentifierProvider provider = ClassIdentifierProviderFactory.getClassIdentifierProvider();
		String teamId = provider.getClassIdentifier(teamClass);
		IBoundTeam teem = classRepository.getTeam(teamClass.getName(), teamId, teamClass.getClassLoader());

		Set<IBoundClass> baseClasses = new HashSet<IBoundClass>();
		for (IBinding binding : teem.getBindings()) {
			String boundClassName = binding.getBoundClass();
			String boundClassIdentifier = provider.getBoundClassIdentifier(teamClass, boundClassName.replace('.', '/'));
			// FIXME(SH): the following may need adaptation for OT/Equinox or other
			// multi-classloader settings:
			IBoundClass boundClass = classRepository.getBoundClass(boundClassName.replace('/', '.'),
					boundClassIdentifier, teamClass.getClassLoader());
			if (baseClasses.add(boundClass))
				boundClass.startTransaction();

			switch (binding.getType()) {
			case FIELD_ACCESS: // fallthrough
			case METHOD_ACCESS:
				IMember member = null;
				if (binding.getType() == IBinding.BindingType.FIELD_ACCESS) {
					member = boundClass.getField(binding.getMemberName(), binding.getMemberSignature());
				} else {
					member = boundClass.getMethod(binding.getMemberName(), binding.getMemberSignature(), 0/* flags */,
							false/* covariantReturn */);
				}

				int memberId = member.getGlobalId(boundClass);
				synchronized (member) {
					addAccessIds(teamClass, teem, binding.getPerTeamId(), memberId);
				}
				//$FALL-THROUGH$
			case ROLE_BASE_BINDING:
				boundClass.handleAddingOfBinding(binding);
				break;
			default: // no action for CALLIN_BINDING here
			}
		}
		for (IBoundClass base : baseClasses) {
			base.commitTransaction();
		}
	}

	/**
	 * Stores the access ids of a team and the corresponding member ids, where
	 * accessId is local to the team and the member id is globally unique.
	 * 
	 * @param teamClass
	 * @param teem
	 * @param accessId
	 * @param memberId
	 * @param stateChange
	 */
	private static void addAccessIds(Class<? extends ITeam> teamClass, IBoundTeam teem, int accessId, int memberId) {
		List<Integer> accessIds = accessIdMap.get(teamClass);
		if (accessIds == null) {
			int highestAccessId = teem.getHighestAccessId() + 1;
			accessIds = new ArrayList<Integer>(highestAccessId);
			for (int i = 0; i <= highestAccessId; i++) {
				accessIds.add(null);
			}
			accessIdMap.put(teamClass, accessIds);
		}
		accessIds.set(accessId, memberId);
	}

	/**
	 * Stores or removes the team
	 * 
	 * @param t
	 * @param callinId
	 * @param joinpointId
	 * @param stateChange
	 */
	private synchronized static void changeTeamsForJoinpoint(ITeam t, int callinId, int joinpointId,
			TeamManager.TeamStateChange stateChange) {
		switch (stateChange) {
		case REGISTER:
			List<ITeam> teams = _teams.get(joinpointId);
			teams.add(0, t);
			List<Integer> callinIds = _callinIds.get(joinpointId);
			callinIds.add(0, callinId);
			break;
		case UNREGISTER:
			teams = _teams.get(joinpointId);
			callinIds = _callinIds.get(joinpointId);
			int index = teams.indexOf(t);
			while (index > -1) {
				teams.remove(index);
				callinIds.remove(index);
				index = teams.indexOf(t);
			}
			break;
		default:
			throw new RuntimeException("Unknown team state change: " + stateChange.name());
		}

		// If size > ID than there are already SwitchPoints for that joinpoint which
		// should be invalidated
		if (_switchpoints.size() > joinpointId) {
			List<SwitchPoint> list = _switchpoints.remove(joinpointId);
			_switchpoints.add(joinpointId, new ArrayList<>());
			SwitchPoint.invalidateAll(list.toArray(new SwitchPoint[list.size()]));
		}
	}

	/**
	 * Merge the teams and callin ids of two joinpoints into the second. This is
	 * used so that activating teams for the second joinpoint (subclass) will
	 * include the effect of activation of the former (superclass).
	 * 
	 * @param superClass
	 * @param subClass
	 * @param superMethod
	 * @param subMethod
	 */
	public static void mergeJoinpoints(IBoundClass superClass, IBoundClass subClass, final IMethod superMethod,
			IMethod subMethod, final boolean handleCovariantReturn) {
		if (subClass.isAnonymous()) {
			// destClass is a placeholder, schedule real merging for when a real subclass
			// gets wired:
			subClass.addWiringTask(new ISubclassWiringTask() {
				public void wire(IBoundClass superClass, IBoundClass subClass) {
					IMethod subMethod = subClass.getMethod(superMethod.getName(), superMethod.getSignature(),
							0/* flags */, handleCovariantReturn);
					mergeJoinpoints(superClass, subClass, superMethod, subMethod, handleCovariantReturn);
				}
			});
			return;
		}
		synchronized (subClass) {
			while (superClass != null && !superClass.isJavaLangObject()) {
				Integer superJoinpointId = getJoinpointId(superClass.getMethodIdentifier(superMethod));
				if (superJoinpointId != null) {
					int subJoinpointId = getJoinpointId(subClass.getMethodIdentifier(subMethod));

					List<Integer> subJoinpoints = joinpointToSubJoinpoints.get(superJoinpointId);
					if (subJoinpoints == null) {
						subJoinpoints = new ArrayList<Integer>();
						joinpointToSubJoinpoints.put(superJoinpointId, subJoinpoints);
					}
					// already processed?
					if (!subJoinpoints.contains(subJoinpointId)) {
						subJoinpoints.add(subJoinpointId);
						applyJoinpointMerge(superJoinpointId, subJoinpointId);
					}
				}
				superClass = superClass.getSuperclass();
			}
		}
	}

	private synchronized static void applyJoinpointMerge(Integer srcJoinpointId, int destJoinpointId) {
		List<ITeam> teams = _teams.get(destJoinpointId);
		List<Integer> callinIds = _callinIds.get(destJoinpointId);
		List<ITeam> srcTeams = _teams.get(srcJoinpointId);
		List<Integer> srcCallins = _callinIds.get(srcJoinpointId);
		for (int s = 0; s < srcTeams.size(); s++) {
			int d = 0; // FIXME(SH): find insertion index based on activation priority!!
			ITeam srcTeam = srcTeams.get(s);
			Integer srcCallin = srcCallins.get(s);
			int idx = teams.indexOf(srcTeam);
			if (idx != -1 && idx < callinIds.size()) {
				if (callinIds.get(idx) == srcCallin)
					continue;
			}
			teams.add(d, srcTeam);
			callinIds.add(0, srcCallin);
		}
		// transitively pass the new information down the tree of subJoinpoints:
		List<Integer> destDests = joinpointToSubJoinpoints.get(destJoinpointId);
		if (destDests != null && !destDests.isEmpty())
			for (Integer destDest : destDests)
				applyJoinpointMerge(destJoinpointId, destDest);
	}

	/**
	 * A "mailbox" for pending tasks (per thread). The weaver stores redefinition
	 * tasks here that failed because the class was in a state between load-time
	 * transformation and definition.
	 */
	public static ThreadLocal<Runnable> pendingTasks = new ThreadLocal<Runnable>();

	/**
	 * Perform any tasks that are pending for this thread: redefinitions of a class
	 * that was in a state between load-time transformation and definition.
	 */
	public static void performPendingTask() {
		Runnable task = pendingTasks.get();
		if (task != null) {
//			System.out.println("pending "+task);
			pendingTasks.set(null);
			try {
				task.run();
			} catch (Throwable t) {
				System.err.println(t + " because " + t.getCause());
				pendingTasks.set(task); // no success try later ??
			}
		}
	}

	public synchronized static void registerSwitchPoint(SwitchPoint newSwitchPoint, int joinpointId) {
		if (_switchpoints.size() <= joinpointId) {
			List<SwitchPoint> list = new ArrayList<>();
			list.add(newSwitchPoint);
			// TODO Lars: What was the idea here?
			// Fill empty space to circumvent IndexOutOfBoundsException
			for (int i = _switchpoints.size(); i < joinpointId; ++i) {
				_switchpoints.add(new ArrayList<>());
			}
			_switchpoints.add(joinpointId, list);
		} else {
			List<SwitchPoint> list = _switchpoints.get(joinpointId);
			list.add(newSwitchPoint);
		}
	}
	
	public synchronized static SwitchPoint getSwitchPoint(int joinpointId) {
		if (_switchpoints.size() > joinpointId) {
			List<SwitchPoint> list = _switchpoints.get(joinpointId);
			if(list.size() == 0) {
				return null;
			}
			return list.get(0);
		}
		return null;
	}

	public static List<IBinding> getPrecedenceSortedCallinBindings(ITeam team, String joinpoint) {
		IClassIdentifierProvider provider = ClassIdentifierProviderFactory.getClassIdentifierProvider();
		Class<? extends ITeam> teamClass = team.getClass();
		String teamId = provider.getClassIdentifier(teamClass);
		IBoundTeam boundTeam = classRepository.getTeam(teamClass.getName(), teamId, teamClass.getClassLoader());

		@SuppressWarnings("unlikely-arg-type")
		List<IBinding> result = boundTeam.getBindings().stream()
				.filter(b -> IBinding.BindingType.CALLIN_BINDING.equals(b.getType()))
				.filter(b -> joinpoint.equals(getBindingJoinpoint(b, teamClass)))
				.collect(Collectors.toList());
				
		return result;
	}

	private static String getBindingJoinpoint(IBinding binding, Class<?> teamClass) {
		IClassIdentifierProvider provider = ClassIdentifierProviderFactory.getClassIdentifierProvider();
		// OTDRE cannot add methods into a sub base, hence we have to use the declaring
		// base class for static methods:
		// (see https://bugs.eclipse.org/435136#c1)
		String boundClassName = ((binding.getBaseFlags() & IBinding.STATIC_BASE) != 0)
				? binding.getDeclaringBaseClassName()
				: binding.getBoundClass();
		String boundClassIdentifier = provider.getBoundClassIdentifier(teamClass, boundClassName);
		String bindingJoinpoint = boundClassIdentifier + "." + binding.getMemberName() + binding.getMemberSignature();
		bindingJoinpoint = bindingJoinpoint.substring(0, bindingJoinpoint.indexOf(')') + 1);
		return bindingJoinpoint;
	}
}
