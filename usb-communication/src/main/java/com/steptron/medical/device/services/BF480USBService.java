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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.usb.UsbControlIrp;
import javax.usb.UsbDevice;
import javax.usb.UsbException;
import javax.usb.UsbIrp;
import javax.usb.UsbPipe;

import org.usb4java.javax.DeviceNotFoundException;

import com.steptron.medical.device.domain.BF480Measurement;
import com.steptron.medical.device.exception.DeviceConnectionException;
import com.steptron.medical.device.util.BytesManipulator;

/**
 * The Class BF480USBService extends an abstract class USBService.
 * This class has specific implementations related to Beurer BF480 Diagnostic scale.
 * This class contains the method implementations such as, device initialisation, device termination
 * and method to get the number of readings the device has stored.
 */
public class BF480USBService extends USBService {

	public static final short VENDOR_ID = (short) 0x04d9;
	public static final short PRODUCT_ID = (short) 0x8010;

	/** The Constant MAX_NUMBER_OF_READINGS represents that the beurer BF480 has maximum of 64 readings stored per user. */
	public static final int MAX_NUMBER_OF_READINGS = 64;

	@Override
	public Collection<?> getMeasurements(String user) throws DeviceNotFoundException, DeviceConnectionException, SecurityException, UsbException {
		int userNumber = Integer.valueOf(user);
		int readingStartByteNumber = (userNumber - 1) * 6;

		List<BF480Measurement> measurements = new ArrayList<BF480Measurement>();
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

			//Read 128 x 64 data from serial interface
			byte[][] rawReadings = new byte[BF480USBService.MAX_NUMBER_OF_READINGS][BF480USBService.BYTE_ARRAY_LENGTH_128];
			for(int readingsCounter = 0; readingsCounter < BF480USBService.MAX_NUMBER_OF_READINGS; readingsCounter++) {
				rawReadings[readingsCounter] = readData(connectionPipe, 128);
			}

			//Convert 128 x 64 data to 64 x 64
			int[][] readings = new int[BF480USBService.MAX_NUMBER_OF_READINGS][BF480USBService.BYTE_ARRAY_LENGTH_128 / 2];
			for(int readingsCounter = 0; readingsCounter < BF480USBService.MAX_NUMBER_OF_READINGS; readingsCounter++) {
				readings[readingsCounter] = BytesManipulator.convertBytesToIntegers(rawReadings[readingsCounter]);
			}

			//Transpose the data matrix to retrieve each patients information
			int[][] userReadings = BytesManipulator.transpose(readings);

			//convert all 64 readings to an object by iterating over rows of the matrix
			for(int readingsCounter = 0; readingsCounter < BF480USBService.MAX_NUMBER_OF_READINGS; readingsCounter++) {
				if(userReadings[readingsCounter][readingStartByteNumber + 4] == 0) {
					break;
				}
				measurements.add(new BF480Measurement(userReadings[readingsCounter], readingStartByteNumber));
			}

			Collections.sort(measurements);
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
		writeDataToInterface(device, usbControl, new byte[] {(byte) 0x10}, DEFAULT_BYTE_ARRAY_LENGTH_8, PADDING_BYTE_0x00);
	}

	/* (non-Javadoc)
	 * @see org.medipi.devices.drivers.service.USBService#getNumberOfReadings(javax.usb.UsbDevice, javax.usb.UsbControlIrp, javax.usb.UsbPipe)
	 */
	@Override
	public int getNumberOfReadings(final UsbDevice device, final UsbControlIrp usbControl, final UsbPipe connectionPipe) throws UsbException {
		return MAX_NUMBER_OF_READINGS;
	}

	/* (non-Javadoc)
	 * @see org.medipi.devices.drivers.service.USBService#readData(javax.usb.UsbPipe, int)
	 */
	@Override
	public byte[] readData(final UsbPipe connectionPipe, final int numberOfBytes) throws UsbException {
		final byte[] data = new byte[numberOfBytes];
		final UsbIrp irp = connectionPipe.asyncSubmit(data);
        irp.waitUntilComplete(3000);

        //This condition is just to check if the data is being read properly.
        if (irp.isUsbException()) {
        	throw new DeviceConnectionException("Unplug and then replug in the Beurer BF480 Diagnostic Scale and press download");
        }
		return data;
	}

	/* (non-Javadoc)
	 * @see org.medipi.devices.drivers.service.USBService#terminateDeviceCommunication(javax.usb.UsbDevice, javax.usb.UsbControlIrp)
	 */
	@Override
	public void terminateDeviceCommunication(final UsbDevice device, final UsbControlIrp usbControl, final UsbPipe connectionPipe) throws UsbException, InterruptedException {
		// Not required to terminate the device communication
	}
}