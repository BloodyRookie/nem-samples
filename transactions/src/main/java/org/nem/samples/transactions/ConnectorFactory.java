package org.nem.samples.transactions;

import org.nem.core.connect.*;
import org.nem.core.connect.client.*;
import org.nem.core.model.Account;

/**
 * A simple NIS connector.
 */
public class ConnectorFactory {
	private static final HttpMethodClient<ErrorResponseDeserializerUnion> CLIENT = createHttpMethodClient();

	public static DefaultAsyncNemConnector<NisApiId> createConnector() {
		final DefaultAsyncNemConnector<NisApiId> connector = new DefaultAsyncNemConnector<>(
				CLIENT,
				r -> { throw new RuntimeException(); });
		connector.setAccountLookup(Account::new);
		return connector;
	}

	private static HttpMethodClient<ErrorResponseDeserializerUnion> createHttpMethodClient() {
		final int connectionTimeout = 4000;
		final int socketTimeout = 10000;
		final int requestTimeout = 30000;
		return new HttpMethodClient<>(connectionTimeout, socketTimeout, requestTimeout);
	}
}
