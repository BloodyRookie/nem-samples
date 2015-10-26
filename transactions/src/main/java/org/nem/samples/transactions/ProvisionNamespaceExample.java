package org.nem.samples.transactions;

import org.nem.core.async.SleepFuture;
import org.nem.core.connect.HttpJsonPostRequest;
import org.nem.core.connect.client.*;
import org.nem.core.crypto.*;
import org.nem.core.model.*;
import org.nem.core.model.namespace.*;
import org.nem.core.model.ncc.*;
import org.nem.core.model.primitive.Amount;
import org.nem.core.node.*;
import org.nem.core.serialization.*;
import org.nem.core.time.*;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Sample console application showing how to provision a namespace.
 * This code is provided to demonstrate basic usage of nem core.
 * For a more complete documentation of the API used see
 * http://bob.nem.ninja/docs
 */
public class ProvisionNamespaceExample {
	private static final Logger LOGGER = Logger.getLogger(ProvisionNamespaceExample.class.getName());

	// Choose mijin network
	static {
		NetworkInfos.setDefault(NetworkInfos.fromFriendlyName("mijinnet"));
	}

	private static final String PRIVATE_KEY_HEX = "73eb2169a871e081bacea12e7be25a0ad284b43207e39d51d4837a54c65a28c2";
	private static final Account SENDER = new Account(new KeyPair(PrivateKey.fromHexString(PRIVATE_KEY_HEX)));

	public static void main(String[] args) {
		provisionNamespace();
		LOGGER.info("finished");
		System.exit(1);
	}

	private static void provisionNamespace() {
		// The namespace 'examples.mijin' is already registered in the block chain.
		// The string below will be appended to form the new namespace, in out example 'examples.mijin.bank'
		// !!! replace this with another string every time you run the sample !!!
		final String newNamespacePart = "bank";
		final NamespaceIdPart newPart = new NamespaceIdPart(newNamespacePart);
		final NamespaceId parent = new NamespaceId("examples.mijin");
		if (!createAndSend(SENDER, newPart, parent)) {
			LOGGER.warning("Namespace provisioning failed");
			return;
		}

		LOGGER.info("Waiting 30 seconds for the transaction to get included into the block chain, please have patience");
		for (int i=0; i<30; i++) {
			System.out.print(30 - i + "...");
			SleepFuture.create(1000).join();
		}

		System.out.println();
		LOGGER.info("Retrieving namespace information from block chain");

		// Retrieve information about the namespace.
		final NamespaceId id = parent.concat(newPart);
		final Namespace namespace = retrieveNamespace(id);
		LOGGER.info(String.format("namespace: %s", namespace.getId()));
		LOGGER.info(String.format("owner: %s", namespace.getOwner().toString()));
		LOGGER.info(String.format("root namespace provisioned at block height: %s", namespace.getHeight()));
	}

	private static boolean createAndSend(final Account sender, final NamespaceIdPart newPart, final NamespaceId parent) {
		final boolean[] success = { false };
		final Transaction transaction = createTransaction(
				Globals.TIME_PROVIDER.getCurrentTime(),
				sender,
				newPart,
				parent);
		final byte[] data = BinarySerializer.serializeToBytes(transaction.asNonVerifiable());
		final RequestAnnounce request = new RequestAnnounce(data, transaction.getSignature().getBytes());
		final CompletableFuture<Deserializer> future = send(Globals.MIJIN_NODE_ENDPOINT, request);
		future.thenAccept(d -> {
					final NemAnnounceResult result = new NemAnnounceResult(d);
					switch (result.getCode()) {
						case 1: LOGGER.info(String.format("successfully provisioned new namespace %s for owner %s",
								null == parent ? newPart.toString() : parent.concat(newPart).toString(),
								sender.getAddress()));
							success[0] = true;
							break;
						default:
							LOGGER.warning(String.format("could not provisioned new namespace %s for owner %s",
									null == parent ? newPart.toString() : parent.concat(newPart).toString(),
									sender.getAddress()));
					}
				})
				.exceptionally(e -> {
					LOGGER.warning(String.format("could not provisioned new namespace %s for owner %s",
							null == parent ? newPart.toString() : parent.concat(newPart).toString(),
							sender.getAddress()));
					return null;
				})
				.join();
		return success[0];
	}

	private static CompletableFuture<Deserializer> send(final NodeEndpoint endpoint, final RequestAnnounce request) {
		return Globals.CONNECTOR.postAsync(
				endpoint,
				NisApiId.NIS_REST_TRANSACTION_ANNOUNCE,
				new HttpJsonPostRequest(request));
	}

	private static ProvisionNamespaceTransaction createTransaction(
		final TimeInstant timeInstant,
		final Account sender,
		final NamespaceIdPart newPart,
		final NamespaceId parent) {
		final ProvisionNamespaceTransaction transaction = new ProvisionNamespaceTransaction(
				timeInstant,
				sender,
				newPart,
				parent);
		transaction.setFee(Amount.fromNem(108));
		transaction.setDeadline(timeInstant.addHours(23));
		transaction.sign();
		return transaction;
	}

	private static Namespace retrieveNamespace(final NamespaceId id) {
		final CompletableFuture<Deserializer> future = Globals.CONNECTOR.getAsync(
				Globals.MIJIN_NODE_ENDPOINT,
				SamplesApiId.NIS_REST_NAMESPACE,
				String.format("namespace=%s", id.toString()));
		final Deserializer deserializer = future.join();
		return new Namespace(deserializer);
	}
}
