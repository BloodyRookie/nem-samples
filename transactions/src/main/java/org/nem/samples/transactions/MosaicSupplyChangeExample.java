package org.nem.samples.transactions;

import org.nem.core.async.SleepFuture;
import org.nem.core.connect.HttpJsonPostRequest;
import org.nem.core.connect.client.NisApiId;
import org.nem.core.crypto.*;
import org.nem.core.model.*;
import org.nem.core.model.mosaic.*;
import org.nem.core.model.namespace.NamespaceId;
import org.nem.core.model.ncc.*;
import org.nem.core.model.primitive.*;
import org.nem.core.node.NodeEndpoint;
import org.nem.core.serialization.*;
import org.nem.core.time.TimeInstant;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Sample console application showing how to change the supply of an existing mosaic.
 * This code is provided to demonstrate basic usage of nem core.
 * For a more complete documentation of the API used see
 * http://bob.nem.ninja/docs
 */
public class MosaicSupplyChangeExample {
	private static final Logger LOGGER = Logger.getLogger(MosaicSupplyChangeExample.class.getName());

	// Choose mijin network
	static {
		NetworkInfos.setDefault(NetworkInfos.fromFriendlyName("mijinnet"));
	}

	private static final String PRIVATE_KEY_HEX = "73eb2169a871e081bacea12e7be25a0ad284b43207e39d51d4837a54c65a28c2";
	private static final Account SENDER = new Account(new KeyPair(PrivateKey.fromHexString(PRIVATE_KEY_HEX)));

	public static void main(String[] args) {
		changeMosaicSupply();
		LOGGER.info("finished");
		System.exit(1);
	}

	private static void changeMosaicSupply() {
		// the mosaic "examples.mijin * euro" is already defined in the block chain.
		final NamespaceId namespaceId = new NamespaceId("examples.mijin");
		final MosaicId mosaicId = new MosaicId(namespaceId, "euro");
		final MosaicIdSupplyPair currentSupply = retrieveMosaicSupply(mosaicId);
		LOGGER.info(String.format("mosaic %s currently has a supply of %d (units)", mosaicId.toString(), currentSupply.getSupply().getRaw()));

		if (!createAndSend(SENDER, mosaicId, 1000000)) {
			LOGGER.warning("mosaic supply change failed");
			return;
		}

		LOGGER.info("Waiting 30 seconds for the transaction to get included into the block chain, please have patience");
		for (int i=0; i<30; i++) {
			System.out.print(30 - i + "...");
			SleepFuture.create(1000).join();
		}

		System.out.println();
		LOGGER.info("Retrieving new mosaic supply information from block chain");
		final MosaicIdSupplyPair newSupply = retrieveMosaicSupply(mosaicId);
		LOGGER.info(String.format("mosaic %s now has a supply of %d (units)", mosaicId.toString(), newSupply.getSupply().getRaw()));
}

	private static boolean createAndSend(final Account sender, final MosaicId mosaicId, final long supplyDelta) {
		final boolean[] success = { false };
		final MosaicSupplyChangeTransaction transaction = createTransaction(
				Globals.TIME_PROVIDER.getCurrentTime(),
				sender,
				mosaicId,
				supplyDelta);
		final byte[] data = BinarySerializer.serializeToBytes(transaction.asNonVerifiable());
		final RequestAnnounce request = new RequestAnnounce(data, transaction.getSignature().getBytes());
		final CompletableFuture<Deserializer> future = send(Globals.MIJIN_NODE_ENDPOINT, request);
		future.thenAccept(d -> {
			final NemAnnounceResult result = new NemAnnounceResult(d);
			switch (result.getCode()) {
				case 1: LOGGER.info(String.format("successfully changed supply for mosaic %s, %d units added ",
						mosaicId,
						supplyDelta));
					success[0] = true;
					break;
				default:
					LOGGER.warning(String.format("could not change supply for mosaic %s, reason: %s",
							mosaicId,
							result.getMessage()));
			}
		})
				.exceptionally(e -> {
					LOGGER.warning(String.format("could not change supply for mosaic %s, reason: %s",
							mosaicId,
							e.getMessage()));
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

	private static MosaicSupplyChangeTransaction createTransaction(
			final TimeInstant timeInstant,
			final Account sender,
			final MosaicId mosaicId,
			final long supplyDelta) {
		final MosaicSupplyChangeTransaction transaction = new MosaicSupplyChangeTransaction(
				timeInstant,
				sender,
				mosaicId,
				MosaicSupplyType.Create,         // increase supply
				Supply.fromValue(supplyDelta));  // change in supply (always in whole units, not subunits)
		transaction.setFee(Amount.fromNem(108));
		transaction.setDeadline(timeInstant.addHours(23));
		transaction.sign();
		return transaction;
	}

	private static MosaicIdSupplyPair retrieveMosaicSupply(final MosaicId id) {
		final CompletableFuture<Deserializer> future = Globals.CONNECTOR.getAsync(
				Globals.MIJIN_NODE_ENDPOINT,
				SamplesApiId.NIS_REST_MOSAIC_SUPPLY,
				String.format("mosaicId=%s", urlEncode(id.toString())));
		final Deserializer deserializer = future.join();
		return new MosaicIdSupplyPair(deserializer);
	}

	private static String urlEncode(final String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("could not encode url encode string");
		}
	}
}
