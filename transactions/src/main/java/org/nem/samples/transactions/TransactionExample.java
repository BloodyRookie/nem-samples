package org.nem.samples.transactions;

import org.nem.core.async.SleepFuture;
import org.nem.core.connect.HttpJsonPostRequest;
import org.nem.core.connect.client.*;
import org.nem.core.crypto.*;
import org.nem.core.model.*;
import org.nem.core.model.ncc.*;
import org.nem.core.model.primitive.Amount;
import org.nem.core.node.NodeEndpoint;
import org.nem.core.serialization.*;
import org.nem.core.time.*;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Sample console application showing how to create transactions and publish them to the mijin network.
 */
public class TransactionExample {
	private static final Logger LOGGER = Logger.getLogger(TransactionExample.class.getName());
	private static final TimeProvider TIME_PROVIDER = new SystemTimeProvider();
	private static final NodeEndpoint MIJIN_NODE_ENDPOINT = new NodeEndpoint("http", <ask_dev_team_for_ip>, 7895);
	private static final DefaultAsyncNemConnector<NisApiId> CONNECTOR = SimpleNisConnector.createConnector();
	private static final SecureRandom RANDOM = new SecureRandom();

	// Choose mijin network
	static {
		NetworkInfos.setDefault(NetworkInfos.fromFriendlyName("mijinnet"));
	}

	// Private keys for the accounts as hex string
	private static final List<String> PRIVATE_KEYS_HEX = Arrays.asList(
			"73eb2169a871e081bacea12e7be25a0ad284b43207e39d51d4837a54c65a28c2",
			"107aa7809ae79626ca18de05a737e25d006dd43ca1588ffd38be68ad51aa02eb",
			"77844ca81b5bc2e62ada6d61465c505e675a11bef44def340cd9f40a08c733c9",
			"08eaf329c1b07b5362977ef16820185031a19dd901326b13417c342147ca0357",
			"d6349533992f5bac7c955b90ed1c17fd5ff5b499273d6bb0b8e77ac8388929b0",
			"9559732458d834fcc3d10e6f5c10c877489ca7431884549a047d1890a4d97bac",
			"9706782a6cb3d149952a63aeb1678bb2c427ff287a346f0503b0f60031f20841",
			"54e01160ead55f9cc22f2db4467164904b00f9aa8eb62abe0fbd56f01bba030d",
			"79c49879da24effa294f236a6e026c426cc715f0110be6ce7be84dc0e232faa0",
			"66b2aaef981c06a293179be916f5c405fb4376a7d306d55587ad9e51c2143376"
	);

	// The accounts
	private static final List<Account> ACCOUNTS = PRIVATE_KEYS_HEX.stream()
			.map(PrivateKey::fromHexString)
			.map(pk -> new Account(new KeyPair(pk)))
			.collect(Collectors.toList());

	public static void main(String[] args) {
		sendSomeXem();
		LOGGER.info("finished");
	}


	private static void sendSomeXem() {
		// do some random transfers between the accounts
		for (int i = 0; i < 10; i++) {
			// send one transaction every second
			final Account sender = ACCOUNTS.get(RANDOM.nextInt(10));
			final Account recipient = ACCOUNTS.get(RANDOM.nextInt(10));
			final long amount = RANDOM.nextInt(1000);
			SleepFuture.create(1000).thenAccept(v -> createAndSend(sender, recipient, amount)).join();
		}
	}

	private static void createAndSend(final Account sender, final Account recipient, final long amount) {
		final Transaction transaction = createTransaction(
				TIME_PROVIDER.getCurrentTime(),
				sender,
				recipient,
				amount);
		final byte[] data = BinarySerializer.serializeToBytes(transaction.asNonVerifiable());
		final RequestAnnounce request = new RequestAnnounce(data, transaction.getSignature().getBytes());
		final CompletableFuture<Deserializer> future = send(MIJIN_NODE_ENDPOINT, request);
		future.thenAccept(d -> {
					final NemAnnounceResult result = new NemAnnounceResult(d);
					switch (result.getCode()) {
						case 1: LOGGER.info(String.format("successfully send %d micro xem from %s to %s",
								amount,
								sender.getAddress(),
								recipient.getAddress()));
							break;
						default:
							LOGGER.warning(String.format("could not send xem from %s to %s, reason: %s",
									sender.getAddress(),
									recipient.getAddress(),
									result.toString()));
					}
				})
				.exceptionally(e -> {
					LOGGER.warning(String.format("could not send xem from %s to %s, reason: %s",
							sender.getAddress(),
							recipient.getAddress().getEncoded(),
							e.getMessage()));
					return null;
				})
				.join();
	}

	private static CompletableFuture<Deserializer> send(final NodeEndpoint endpoint, final RequestAnnounce request) {
		return CONNECTOR.postAsync(
				endpoint,
				NisApiId.NIS_REST_TRANSACTION_ANNOUNCE,
				new HttpJsonPostRequest(request));
	}

	private static Transaction createTransaction(
			final TimeInstant timeInstant,
			final Account sender,
			final Account recipient,
			final long amount) {
		final TransferTransaction transaction = new TransferTransaction(
				1,                                // version
				timeInstant,                      // time instant
				sender,                           // sender
				recipient,                        // recipient
				Amount.fromMicroNem(amount),      // amount in micro xem
				null);                            // attachment (message, mosaics)
		transaction.setFee(Amount.fromNem(200));
		transaction.setDeadline(timeInstant.addHours(23));
		transaction.sign();
		return transaction;
	}
}
