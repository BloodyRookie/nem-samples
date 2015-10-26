package org.nem.samples.transactions;

import org.nem.core.node.ApiId;

/**
 * NIS REST API paths that are not used in normal NIS operations.
 * Only added for some samples to work.
 */
public enum SamplesApiId implements ApiId {

	/**
	 * The /namespace API
	 */
	NIS_REST_NAMESPACE("/namespace");

	private final String value;

	/**
	 * Creates a samples API id.
	 *
	 * @param value The string representation.
	 */
	SamplesApiId(final String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return this.value;
	}
}
