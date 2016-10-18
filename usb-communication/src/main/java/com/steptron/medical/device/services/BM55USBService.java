/*
 *
 * Copyright (C) 2016 Krishna Kuntala
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.steptron.medical.device.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.usb.UsbControlIrp;
import javax.usb.UsbDevice;
import javax.usb.UsbException;
import javax.usb.UsbIrp;
import javax.usb.UsbPipe;

import org.usb4java.javax.DeviceNotFoundException;

import com.steptron.medical.device.domain.BM55Measurement;
import com.steptron.medical.device.domain.BM55User;
import com.steptron.medical.device.exception.DeviceConnectionException;

/**
 * The Class BM55USBService extends an abstract class USBService.
 * This class has specific implementations related to Beurer BM55 blood pressure monitor device.
 * This class contains the method implementations such as, device initialisation, device termination
 * and method to get the number of readings the device has stored.
 */
public class BM55USBService extends USBService {

	public static final short VENDOR_ID = (short) 0x0c45;
	public static final short PRODUCT_ID = (short) 0x7406;

	@Override
	public Collection<?> getMeasurements(String user) throws DeviceNotFoundException, DeviceConnectionException, SecurityException, UsbException, InterruptedException {
		BM55User readingsUser = BM55User.valueOf(user);
		List<BM55Measurement> measurements = new ArrayList<BM55Measurement>();
		UsbDevice device = getUSBDevice(VENDOR_ID, PRODUCT_ID);

		UsbPipe connectionPipe = getUSBConnection(device, 0, -127);
		byte requestType = 33;
		byte request = 0x09;
		short value = 521;
		short index = 0;
		UsbControlIrp usbControl = getUSBControl(device, requestType, request, value, index);
		try {
			connectionPipe.open();
			initialiseDevice(device, usbControl, connectionPipe);
			int numberOfReadings = getNumberOfReadings(device, usbControl, connectionPipe);

			BM55Measurement measurement;
			byte[] data;
			for(int readingsCounter = 1; readingsCounter < numberOfReadings; readingsCounter++) {
				writeDataToInterface(device, usbControl, new byte[] {(byte) 0xA3, (byte) readingsCounter}, BM55USBService.DEFAULT_BYTE_ARRAY_LENGTH_8, BM55USBService.PADDING_BYTE_0xF4);
				data = readData(connectionPipe, 8);
				measurement = new BM55Measurement(data);
				if(measurement.getUser().equals(readingsUser)) {
					measurements.add(new BM55Measurement(data));
				}
			}
			terminateDeviceCommunication(device, usbControl, connectionPipe);
		} finally {
			if(connectionPipe != null && connectionPipe.isOpen()) {
				try {
					connectionPipe.close();
					connectionPipe.getUsbEndpoint().getUsbInterface().release();
				} catch(UsbException e) {
					//Do nothing
				}
			}
		}
		return measurements;
	}

	/* (non-Javadoc)
	 * @see org.medipi.devices.drivers.service.USBService#initialiseDevice(javax.usb.UsbDevice, javax.usb.UsbControlIrp, javax.usb.UsbPipe)
	 */
	@Override
	public void initialiseDevice(final UsbDevice device, final UsbControlIrp usbControl, final UsbPipe connectionPipe) throws UsbException {
		writeDataToInterface(device, usbControl, new byte[] {(byte) 0xAA}, DEFAULT_BYTE_ARRAY_LENGTH_8, PADDING_BYTE_0xF4);
		readData(connectionPipe, 8);
	}

	/* (non-Javadoc)
	 * @see org.medipi.devices.drivers.service.USBService#getNumberOfReadings(javax.usb.UsbDevice, javax.usb.UsbControlIrp, javax.usb.UsbPipe)
	 */
	@Override
	public int getNumberOfReadings(final UsbDevice device, final UsbControlIrp usbControl, final UsbPipe connectionPipe) throws UsbException {
		writeDataToInterface(device, usbControl, new byte[] {(byte) 0xA2}, DEFAULT_BYTE_ARRAY_LENGTH_8, PADDING_BYTE_0xF4);
		final byte[] data = readData(connectionPipe, 8);
		return data[0];
	}

	/* (non-Javadoc)
	 * @see org.medipi.devices.drivers.service.USBService#readData(javax.usb.UsbPipe, int)
	 */
	@Override
	public byte[] readData(final UsbPipe connectionPipe, final int numberOfBytes) throws UsbException {
		final byte[] data = new byte[numberOfBytes];
		final UsbIrp irp = connectionPipe.asyncSubmit(data);
        irp.waitUntilComplete(500);

        //This condition is just to check if the data is being read properly. Input and output data cannot be the same if the device is responding.
        if (irp.isUsbException() || Arrays.equals(data, new byte[numberOfBytes])) {
        	throw new DeviceConnectionException("Unplug and then replug in the Beurer BM55 Blood Pressure Monitor and press download");
        }
		return data;
	}

	/* (non-Javadoc)
	 * @see org.medipi.devices.drivers.service.USBService#terminateDeviceCommunication(javax.usb.UsbDevice, javax.usb.UsbControlIrp)
	 */
	@Override
	public void terminateDeviceCommunication(final UsbDevice device, final UsbControlIrp usbControl, final UsbPipe connectionPipe) throws UsbException, InterruptedException {
		writeDataToInterface(device, usbControl, new byte[] {(byte) 0xF7}, DEFAULT_BYTE_ARRAY_LENGTH_8, PADDING_BYTE_0xF4);
		try {
			readData(connectionPipe, 8);
		} catch(DeviceConnectionException e) {
			//do nothing
		}
	}
}
