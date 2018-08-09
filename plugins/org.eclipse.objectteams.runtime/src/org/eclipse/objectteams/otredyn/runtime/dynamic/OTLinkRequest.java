package org.eclipse.objectteams.otredyn.runtime.dynamic;

public class OTLinkRequest {

	private DynamicCallSiteDescriptor descriptor;
	private Object[] arguments;

	public OTLinkRequest(DynamicCallSiteDescriptor descriptor, Object... arguments) {
		this.descriptor = descriptor;
		this.arguments = arguments;
	}

	public Object[] getArguments() {
		return arguments != null ? arguments.clone() : null;
	}

	public DynamicCallSiteDescriptor getCallSiteDescriptor() {
		return descriptor;
	}

	public Object getReceiver() {
		return arguments != null && arguments.length > 0 ? arguments[0] : null;
	}

}
