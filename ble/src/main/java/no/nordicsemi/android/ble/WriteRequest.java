package no.nordicsemi.android.ble;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import no.nordicsemi.android.ble.callback.DataSplitter;
import no.nordicsemi.android.ble.callback.DefaultMtuSplitter;
import no.nordicsemi.android.ble.callback.FailCallback;
import no.nordicsemi.android.ble.callback.SuccessCallback;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class WriteRequest extends Request {
	private final static DataSplitter MTU_SPLITTER = new DefaultMtuSplitter();

	private DataSplitter dataSplitter;
	private final byte[] data;
	private final int writeType;
	private int count = 0;

	WriteRequest(final @NonNull Type type, final @Nullable BluetoothGattCharacteristic characteristic,
				 final @Nullable byte[] data, final int offset, final int length, final int writeType) {
		super(type, characteristic);
		this.data = copy(data, offset, length);
		this.writeType = writeType;
	}

	WriteRequest(final @NonNull Type type, final @Nullable BluetoothGattDescriptor descriptor,
				 final @Nullable byte[] data, final int offset, final int length) {
		super(type, descriptor);
		this.data = copy(data, offset, length);
		this.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
	}

	private static byte[] copy(final @Nullable byte[] value, final int offset, final int length) {
		if (value == null || offset > value.length)
			return null;
		final int maxLength = Math.min(value.length - offset, length);
		final byte[] copy = new byte[maxLength];
		System.arraycopy(value, offset, copy, 0, maxLength);
		return copy;
	}

	@Override
	@NonNull
	public WriteRequest done(final @NonNull SuccessCallback callback) {
		this.successCallback = callback;
		return this;
	}

	@Override
	@NonNull
	public WriteRequest fail(final @NonNull FailCallback callback) {
		this.failCallback = callback;
		return this;
	}

	/**
	 * Adds a splitter that will be used to cut given data into multiple packets.
	 * The splitter may modify each packet if necessary.
	 * @return the request
	 */
	@NonNull
	public WriteRequest split(final @NonNull DataSplitter splitter) {
		this.dataSplitter = splitter;
		return this;
	}

	/**
	 * Adds a default MTU splitter that will be used to cut given data into at-most MTU-3
	 * bytes long packets.
	 * @return the request
	 */
	@NonNull
	public WriteRequest split() {
		this.dataSplitter = MTU_SPLITTER;
		return this;
	}

	byte[] getData(final int mtu) {
		if (dataSplitter == null || data == null)
			return data;

		final byte[] chunk = dataSplitter.chunk(data, count++, mtu - 3);
		if (chunk == null) // all data were sent
			count = 0;
		return chunk;
	}

	boolean hasMore() {
		return count > 0;
	}

	int getWriteType() {
		return writeType;
	}
}