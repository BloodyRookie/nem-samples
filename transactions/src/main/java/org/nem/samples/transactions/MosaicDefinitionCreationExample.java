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
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Sample console application showing how to create a mosaic definition.
 * This code is provided to demonstrate basic usage of nem core.
 * For a more complete documentation of the API used see
 * http://bob.nem.ninja/docs
 */
public class MosaicDefinitionCreationExample {
	private static final Logger LOGGER = Logger.getLogger(MosaicDefinitionCreationExample.class.getName());

	// Choose mijin network
	static {
		NetworkInfos.setDefault(NetworkInfos.fromFriendlyName("mijinnet"));
	}

	private static final String PRIVATE_KEY_HEX = "73eb2169a871e081bacea12e7be25a0ad284b43207e39d51d4837a54c65a28c2";
	private static final Account SENDER = new Account(new KeyPair(PrivateKey.fromHexString(PRIVATE_KEY_HEX)));

	public static void main(String[] args) {
		createMosaicDefinition();
		LOGGER.info("finished");
		System.exit(1);
	}

	private static void createMosaicDefinition() {
		// The namespace 'examples.mijin' is already registered in the block chain.
		// The string below will be appended to form the mosaic id, in out example 'examples.mijin * dollar'
		// !!! replace this with another string every time you run the sample !!!
		final String mosaicName = "dollar";
		final NamespaceId namespaceId = new NamespaceId("examples.mijin");
		if (!createAndSend(SENDER, namespaceId, mosaicName)) {
			LOGGER.warning("mosaic definition creation failed");
			return;
		}

		LOGGER.info("Waiting 30 seconds for the transaction to get included into the block chain, please have patience");
		for (int i=0; i<30; i++) {
			System.out.print(30 - i + "...");
			SleepFuture.create(1000).join();
		}

		System.out.println();
		LOGGER.info("Retrieving mosaic definition information from block chain");

		// Retrieve information about the mosaic definition.
		final MosaicId id = new MosaicId(namespaceId, mosaicName);
		final MosaicDefinition mosaicDefinition = retrieveMosaicDefinition(id);
		LOGGER.info(String.format("MosaicId: %s", mosaicDefinition.getId()));
		LOGGER.info(String.format("creator: %s", mosaicDefinition.getCreator()));
		LOGGER.info(String.format("description: %s", mosaicDefinition.getDescriptor()));
		final MosaicProperties properties = mosaicDefinition.getProperties();
		LOGGER.info(String.format("initial supply : %s", properties.getInitialSupply()));
		LOGGER.info(String.format("divisibility : %s", properties.getDivisibility()));
		LOGGER.info(String.format("supply mutable? %s", properties.isSupplyMutable()));
		LOGGER.info(String.format("transferable? %s", properties.isTransferable()));
		final MosaicLevy levy = mosaicDefinition.getMosaicLevy();
		if (null != levy) {
			LOGGER.info(String.format("levy type: %s", levy.getType().toString()));
			LOGGER.info(String.format("levy recipient: %s", levy.getRecipient()));
			LOGGER.info(String.format("levy mosaic id: %s", levy.getMosaicId()));
			LOGGER.info(String.format("levy fee: %s", levy.getFee()));
		}
	}

	private static boolean createAndSend(final Account sender, final NamespaceId namespaceId, final String mosaicName) {
		final boolean[] success = { false };
		final MosaicDefinitionCreationTransaction transaction = createTransaction(
				Globals.TIME_PROVIDER.getCurrentTime(),
				sender,
				namespaceId,
				mosaicName);
		final byte[] data = BinarySerializer.serializeToBytes(transaction.asNonVerifiable());
		final RequestAnnounce request = new RequestAnnounce(data, transaction.getSignature().getBytes());
		final CompletableFuture<Deserializer> future = send(Globals.MIJIN_NODE_ENDPOINT, request);
		future.thenAccept(d -> {
			final NemAnnounceResult result = new NemAnnounceResult(d);
			switch (result.getCode()) {
				case 1: LOGGER.info(String.format("successfully created mosaic definition %s for owner %s",
						transaction.getMosaicDefinition().getId(),
						sender.getAddress()));
					success[0] = true;
					break;
				default:
					LOGGER.warning(String.format("could not create mosaic definition %s for owner %s",
							transaction.getMosaicDefinition().getId(),
							sender.getAddress()));
			}
		})
				.exceptionally(e -> {
					LOGGER.warning(String.format("could not create mosaic definition %s for owner %s",
							transaction.getMosaicDefinition().getId(),
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

	private static MosaicDefinitionCreationTransaction createTransaction(
			final TimeInstant timeInstant,
			final Account sender,
			final NamespaceId namespaceId,
			final String mosaicName) {
		final MosaicDefinitionCreationTransaction transaction = new MosaicDefinitionCreationTransaction(
				timeInstant,
				sender,
				createMosaicDefinition(sender, namespaceId, mosaicName));
		transaction.setFee(Amount.fromNem(108));
		transaction.setDeadline(timeInstant.addHours(23));
		transaction.sign();
		return transaction;
	}

	// The creator of a mosaic definition must always be the sender of the transaction that publishes the mosaic definition.
	// The namespace in which the mosaic will reside is given by its id.
	// The mosaic name within the namespace must be unique.
	private static MosaicDefinition createMosaicDefinition(
			final Account creator,
			final NamespaceId namespaceId,
			final String mosaicName) {
		final MosaicId mosaicId = new MosaicId(namespaceId, mosaicName);
		final MosaicDescriptor descriptor = new MosaicDescriptor("provide a description for the mosaic here");

		// This shows how properties of the mosaic can be chosen.
		// If no custom properties are supplied default values are taken.
		final Properties properties = new Properties();
		properties.put("initialSupply", Long.toString(1000000));
		properties.put("divisibility", Long.toString(1));
		properties.put("supplyMutable", Boolean.toString(true));
		properties.put("transferable", Boolean.toString(true));
		final MosaicProperties mosaicProperties = new DefaultMosaicProperties(properties);

		// Optionally a levy can be supplied. It is allowed to be null.
		final MosaicLevy levy = new MosaicLevy(
				MosaicTransferFeeType.Absolute, // levy specfies an absolute value
				creator,                        // levy is send to the creator
				MosaicConstants.MOSAIC_ID_XEM,  // levy is paid in XEM
				Quantity.fromValue(1000)        // Each transfer of the mosaic will transfer 1000 XEM
				                                // from the mosaic sender to the recipient (here the creator)
		);

		return new MosaicDefinition(creator,mosaicId,descriptor, mosaicProperties, levy);
	}

	private static MosaicDefinition retrieveMosaicDefinition(final MosaicId id) {
		final CompletableFuture<Deserializer> future = Globals.CONNECTOR.getAsync(
				Globals.MIJIN_NODE_ENDPOINT,
				SamplesApiId.NIS_REST_MOSAIC_DEFINITION,
				String.format("mosaicId=%s", urlEncode(id.toString())));
		final Deserializer deserializer = future.join();
		return new MosaicDefinition(deserializer);
	}

	private static String urlEncode(final String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("could not encode url encode string");
		}
	}
}
