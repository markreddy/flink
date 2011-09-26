/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.nephele.io.channels.bytebuffered;

import java.io.EOFException;
import java.io.IOException;

import eu.stratosphere.nephele.event.task.AbstractEvent;
import eu.stratosphere.nephele.event.task.AbstractTaskEvent;
import eu.stratosphere.nephele.io.InputGate;
import eu.stratosphere.nephele.io.RecordDeserializer;
import eu.stratosphere.nephele.io.channels.AbstractInputChannel;
import eu.stratosphere.nephele.io.channels.Buffer;
import eu.stratosphere.nephele.io.channels.ChannelID;
import eu.stratosphere.nephele.io.channels.ChannelType;
import eu.stratosphere.nephele.io.channels.DeserializationBuffer;
import eu.stratosphere.nephele.io.compression.CompressionEvent;
import eu.stratosphere.nephele.io.compression.CompressionLevel;
import eu.stratosphere.nephele.io.compression.CompressionLoader;
import eu.stratosphere.nephele.io.compression.Decompressor;
import eu.stratosphere.nephele.types.Record;

/**
 * Byte buffered input channels are a concrete implementation of input channels. Byte buffered input channels directly
 * read records to a TCP stream which is
 * generated by the corresponding network output channel. Two vertices connected by a network channel must run in the
 * same stage (i.e. at the same time) but may
 * run on different instances.
 * 
 * @author warneke
 * @param <T>
 *        the type of record that can be transported through this channel
 */
public abstract class AbstractByteBufferedInputChannel<T extends Record> extends AbstractInputChannel<T> {

	/**
	 * The deserialization buffer used to deserialize records.
	 */
	private final DeserializationBuffer<T> deserializationBuffer;

	private Buffer compressedDataBuffer = null;

	private ByteBufferedInputChannelBroker inputChannelBroker = null;

	private final Object synchronisationObject = new Object();

	private boolean brokerAggreedToCloseChannel = false;

	private T bufferedRecord = null;

	/**
	 * The Decompressor-Object to decompress incoming data
	 */
	private final Decompressor decompressor;

	/**
	 * Buffer for the uncompressed data.
	 */
	private Buffer uncompressedDataBuffer = null;

	private IOException ioException = null;

	/**
	 * Creates a new network input channel.
	 * 
	 * @param inputGate
	 *        the input gate this channel is wired to
	 * @param channelIndex
	 *        the channel's index at the associated input gate
	 * @param type
	 *        the type of record transported through this channel
	 * @param channelID
	 *        the channel ID to assign to the new channel, <code>null</code> to generate a new ID
	 * @param compressionLevel
	 *        the level of compression to be used for this channel
	 */
	public AbstractByteBufferedInputChannel(InputGate<T> inputGate, int channelIndex,
			RecordDeserializer<T> deserializer, ChannelID channelID, CompressionLevel compressionLevel) {
		super(inputGate, channelIndex, channelID, compressionLevel);
		this.deserializationBuffer = new DeserializationBuffer<T>(deserializer, false);

		this.decompressor = CompressionLoader.getDecompressorByCompressionLevel(compressionLevel, this);
	}

	/**
	 * Deserializes the next record from one of the data buffers.
	 * 
	 * @return the next record or <code>null</code> if all data buffers are exhausted
	 * @throws ExecutionFailureException
	 *         if the record cannot be deserialized
	 */
	private T deserializeNextRecord(final T target) throws IOException {

		if (this.bufferedRecord != null) {
			final T record = this.bufferedRecord;
			this.bufferedRecord = null;
			return record;
		}

		if (this.uncompressedDataBuffer == null) {

			synchronized (this.synchronisationObject) {

				if (this.ioException != null) {
					throw this.ioException;
				}

				requestReadBuffersFromBroker();
			}

			if (this.uncompressedDataBuffer == null) {
				return null;
			}

			if (this.decompressor != null) {
				this.decompressor.decompress();
			}
		}

		final T nextRecord = this.deserializationBuffer.readData(target, this.uncompressedDataBuffer);

		if (this.uncompressedDataBuffer.remaining() == 0) {
			releasedConsumedReadBuffer();
			this.bufferedRecord = nextRecord;
			return null;
		}

		return nextRecord;
	}

	private void requestReadBuffersFromBroker() {

		// this.leasedReadBuffer = this.inputChannelBroker.getReadBufferToConsume();
		final BufferPairResponse bufferPair = this.inputChannelBroker.getReadBufferToConsume();

		if (bufferPair == null) {
			return;
		}

		this.compressedDataBuffer = bufferPair.getCompressedDataBuffer();
		this.uncompressedDataBuffer = bufferPair.getUncompressedDataBuffer();

		if (this.decompressor != null) {
			this.decompressor.setCompressedDataBuffer(this.compressedDataBuffer);
			this.decompressor.setUncompressedDataBuffer(this.uncompressedDataBuffer);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T readRecord(final T target) throws IOException {

		if (isClosed()) {
			throw new EOFException();
		}

		return deserializeNextRecord(target);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isClosed() throws IOException {

		// TODO: check for decompressor

		if (this.bufferedRecord != null || this.uncompressedDataBuffer != null) {
			return false;
		}

		synchronized (this.synchronisationObject) {

			if (this.ioException != null) {
				throw this.ioException;
			}

			if (!this.brokerAggreedToCloseChannel) {
				return false;
			}
		}

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException, InterruptedException {

		this.deserializationBuffer.clear();
		if (this.uncompressedDataBuffer != null) {
			releasedConsumedReadBuffer();
		}

		// This code fragment makes sure the isClosed method works in case the channel input has not been fully consumed
		if (this.getType() == ChannelType.NETWORK) {
			synchronized (this.synchronisationObject) {
				if (!this.brokerAggreedToCloseChannel) {
					while (!this.brokerAggreedToCloseChannel) {

						requestReadBuffersFromBroker();
						if (this.uncompressedDataBuffer != null || this.compressedDataBuffer != null) {
							releasedConsumedReadBuffer();
						}
						this.synchronisationObject.wait(500);
					}
					this.bufferedRecord = null;
				}
			}
		}

		/*
		 * Send close event to indicate the input channel has successfully
		 * processed all data it is interested in.
		 */
		final ChannelType type = getType();
		if (type == ChannelType.NETWORK || type == ChannelType.INMEMORY) {
			transferEvent(new ByteBufferedChannelCloseEvent());
		}
	}

	private void releasedConsumedReadBuffer() {

		this.inputChannelBroker.releaseConsumedReadBuffer();
		this.uncompressedDataBuffer = null;
		this.compressedDataBuffer = null;
	}

	public void setInputChannelBroker(ByteBufferedInputChannelBroker inputChannelBroker) {
		this.inputChannelBroker = inputChannelBroker;
	}

	public void checkForNetworkEvents() {

		// Create an event for the input gate queue
		this.getInputGate().notifyRecordIsAvailable(getChannelIndex());
	}

	@Override
	public void processEvent(AbstractEvent event) {

		if (ByteBufferedChannelCloseEvent.class.isInstance(event)) {
			synchronized (this.synchronisationObject) {
				// System.out.println("Received close event");
				this.brokerAggreedToCloseChannel = true;
			}
			// Make sure the application wake's up to check this
			checkForNetworkEvents();
		} else if (AbstractTaskEvent.class.isInstance(event)) {
			// Simply dispatch the event if it comes from a task
			getInputGate().deliverEvent((AbstractTaskEvent) event);
		} else if (CompressionEvent.class.isInstance(event)) {
			final CompressionEvent compressionEvent = (CompressionEvent) event;
			this.decompressor.setCurrentInternalDecompressionLibraryIndex(compressionEvent
				.getCurrentInternalCompressionLibraryIndex());
		} else {
			// TODO: Handle unknown event
			System.out.println("Received unknown event:" + event);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void transferEvent(AbstractEvent event) throws IOException, InterruptedException {

		this.inputChannelBroker.transferEventToOutputChannel(event);
	}

	public void reportIOException(IOException ioe) {

		synchronized (this.synchronisationObject) {
			this.ioException = ioe;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void releaseResources() {

		synchronized (this.synchronisationObject) {
			this.brokerAggreedToCloseChannel = true;
		}

		this.deserializationBuffer.clear();

		// The buffers are recycled by the input channel wrapper

		if (this.decompressor != null) {
			this.decompressor.shutdown(getID());
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void activate() throws IOException, InterruptedException {
		
		transferEvent(new ByteBufferedChannelActivateEvent());
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getAmountOfDataTransmitted() {
		
		//TODO: Implement me
		return 0L;
	}
}
