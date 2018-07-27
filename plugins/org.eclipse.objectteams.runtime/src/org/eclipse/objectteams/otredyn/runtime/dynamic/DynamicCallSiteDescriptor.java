package org.eclipse.objectteams.otredyn.runtime.dynamic;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.StringTokenizer;

public class CallSiteDescriptor {

	private final Lookup lookup;
	private final String[] tokenizedName;
	private final MethodType type;
	private String joinpointDescriptor;

	public CallSiteDescriptor(Lookup lookup, String name, MethodType type, String joinpointDescriptor) {
		this.lookup = lookup;
		this.joinpointDescriptor = joinpointDescriptor;
		this.tokenizedName = tokenizeName(name);
		this.type = type;
	}

	public Lookup getLookup() {
		return lookup;
	}
	
	public String getJoinpointDescriptor() {
		return joinpointDescriptor;
	}

	public String[] getName() {
		return tokenizedName;
	}

	public MethodType getType() {
		return type;
	}

	private String[] tokenizeName(String name) {
		StringTokenizer tokenizer = new StringTokenizer(name, ".");
		String[] tokens = new String[tokenizer.countTokens()];
		for (int i = 0; i < tokens.length; ++i) {
			String token = tokenizer.nextToken();
			tokens[i] = token.intern();
		}
		return tokens;
	}
}
