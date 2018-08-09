package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;

public class DynamicCallSiteDescriptor extends CallSiteDescriptor {

	public static final int CALL_IN = 0;
	public static final int CALL_NEXT = 1;

	private static final int OPERATION_MASK = 1;

	// TODO: GET for future callout implementations
	private static final Operation[] OPERATIONS = new Operation[] { StandardOperation.CALL, StandardOperation.CALL };

	private final int flags;
	private final String name;
	private final String joinpointDescriptor;

	private DynamicCallSiteDescriptor(Lookup lookup, String name, Operation operation, MethodType type,
			String joinpointDescriptor, int flags) {
		super(lookup, operation, type);
		this.name = name;
		this.joinpointDescriptor = joinpointDescriptor;
		this.flags = flags;
	}

	public String getJoinpointDescriptor() {
		return joinpointDescriptor;
	}

	public static DynamicCallSiteDescriptor get(Lookup lookup, String name, MethodType type, String joinpointDescriptor,
			int flags) {
		final int operationIndex = flags & OPERATION_MASK;
		Operation operation = OPERATIONS[operationIndex];
		return new DynamicCallSiteDescriptor(lookup, name, operation, type, joinpointDescriptor, flags);
	}

	public static Operation getBaseOperation(final CallSiteDescriptor desc) {
		return NamespaceOperation.getBaseOperation(NamedOperation.getBaseOperation(desc.getOperation()));
	}

	public static StandardOperation getStandardOperation(final CallSiteDescriptor desc) {
		return (StandardOperation) getBaseOperation(desc);
	}

	public static String getOperand(final CallSiteDescriptor desc) {
		final Operation operation = desc.getOperation();
		return operation instanceof NamedOperation ? ((NamedOperation) operation).getName().toString() : null;
	}

	protected String getName() {
		return name;
	}

	public int getFlags() {
		return flags;
	}

	@Override
	public boolean equals(final Object obj) {
		if (!(obj instanceof DynamicCallSiteDescriptor)) {
			return false;
		}
		return super.equals(obj) && flags == ((DynamicCallSiteDescriptor) obj).flags;
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ flags;
	}
}
