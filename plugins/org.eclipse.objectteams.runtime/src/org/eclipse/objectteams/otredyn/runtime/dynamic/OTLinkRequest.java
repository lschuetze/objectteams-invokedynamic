package org.eclipse.objectteams.otredyn.runtime.dynamic;

public class LinkRequest {

	private CallSiteDescriptor descriptor;
	private Object[] arguments;

	public LinkRequest(CallSiteDescriptor descriptor, Object... arguments) {
		this.descriptor = descriptor;
		this.arguments = arguments;
	}

	public Object[] getArguments() {
		return arguments != null ? arguments.clone() : null;
	}

	public CallSiteDescriptor getCallSiteDescriptor() {
		return descriptor;
	}

	public Object getReceiver() {
		return arguments != null && arguments.length > 0 ? arguments[0] : null;
	}

}
