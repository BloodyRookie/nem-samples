package org.nem.samples.transactions;

import org.nem.core.connect.client.DefaultAsyncNemConnector;
import org.nem.core.node.*;
import org.nem.core.time.*;

/**
 * Some global data.
 */
public class Globals {
	public static final TimeProvider TIME_PROVIDER = new SystemTimeProvider();
	public static final NodeEndpoint MIJIN_NODE_ENDPOINT = new NodeEndpoint("http", <ask_dev_team_for_ip>, 7895);
	public static final DefaultAsyncNemConnector<ApiId> CONNECTOR = ConnectorFactory.createConnector();
}
