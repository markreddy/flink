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

package eu.stratosphere.pact.runtime.task.util;

import java.io.IOException;
import java.util.HashSet;

import junit.framework.Assert;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import eu.stratosphere.nephele.execution.Environment;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.nephele.template.AbstractTask;
import eu.stratosphere.pact.common.type.KeyValuePair;
import eu.stratosphere.pact.common.type.PactRecord;
import eu.stratosphere.pact.common.type.base.PactInteger;

public class OutputCollectorTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testCollect() {

		Class<RecordWriter<PactRecord>> rwC = (Class<RecordWriter<PactRecord>>) ((Class<?>) RecordWriter.class);
		Class<PactRecord> kvpC = (Class<PactRecord>) ((Class<?>) KeyValuePair.class);

		// create readers
		RecordWriter<PactRecord> rwMock1 = new RecordWriter<PactRecord>(
			new MockTask(), kvpC);
		RecordWriter<PactRecord> rwMock2 = new RecordWriter<PactRecord>(
			new MockTask(), kvpC);
		RecordWriter<PactRecord> rwMock3 = new RecordWriter<PactRecord>(
			new MockTask(), kvpC);
		RecordWriter<PactRecord> rwMock4 = new RecordWriter<PactRecord>(
			new MockTask(), kvpC);
		RecordWriter<PactRecord> rwMock5 = new RecordWriter<PactRecord>(
			new MockTask(), kvpC);
		RecordWriter<PactRecord> rwMock6 = new RecordWriter<PactRecord>(
			new MockTask(), kvpC);
		// mock readers
		rwMock1 = Mockito.mock(rwC);
		rwMock2 = Mockito.mock(rwC);
		rwMock3 = Mockito.mock(rwC);
		rwMock4 = Mockito.mock(rwC);
		rwMock5 = Mockito.mock(rwC);
		rwMock6 = Mockito.mock(rwC);

		ArgumentCaptor<PactRecord> captor1 = ArgumentCaptor.forClass(kvpC);
		ArgumentCaptor<PactRecord> captor2 = ArgumentCaptor.forClass(kvpC);
		ArgumentCaptor<PactRecord> captor3 = ArgumentCaptor.forClass(kvpC);
		ArgumentCaptor<PactRecord> captor4 = ArgumentCaptor.forClass(kvpC);
		ArgumentCaptor<PactRecord> captor5 = ArgumentCaptor.forClass(kvpC);
		ArgumentCaptor<PactRecord> captor6 = ArgumentCaptor.forClass(kvpC);

		OutputCollector oc = new OutputCollector();

		oc.addWriter(rwMock1);
		oc.addWriter(rwMock2);
		oc.addWriter(rwMock3);
		oc.addWriter(rwMock4);
		oc.addWriter(rwMock5);
		oc.addWriter(rwMock6);

		PactRecord record = new PactRecord();
		record.addField(new PactInteger(1));
		record.addField(new PactInteger(123));
		oc.collect(record);
		record = new PactRecord();
		record.addField(new PactInteger(23));
		record.addField(new PactInteger(672));
		oc.collect(record);
		record = new PactRecord();
		record.addField(new PactInteger(1673));
		record.addField(new PactInteger(-12));
		oc.collect(record);

		try {
			Mockito.verify(rwMock1, Mockito.times(3)).emit(captor1.capture());
			Mockito.verify(rwMock2, Mockito.times(3)).emit(captor2.capture());
			Mockito.verify(rwMock3, Mockito.times(3)).emit(captor3.capture());
			Mockito.verify(rwMock4, Mockito.times(3)).emit(captor4.capture());
			Mockito.verify(rwMock5, Mockito.times(3)).emit(captor5.capture());
			Mockito.verify(rwMock6, Mockito.times(3)).emit(captor6.capture());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		HashSet<Integer> refs = new HashSet<Integer>();

		// first pair
		refs.add(System.identityHashCode(captor1.getAllValues().get(0)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor2.getAllValues().get(0))));
		refs.add(System.identityHashCode(captor2.getAllValues().get(0)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor3.getAllValues().get(0))));
		refs.add(System.identityHashCode(captor3.getAllValues().get(0)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor4.getAllValues().get(0))));
		refs.add(System.identityHashCode(captor4.getAllValues().get(0)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor6.getAllValues().get(0))));
		refs.add(System.identityHashCode(captor6.getAllValues().get(0)));
		Assert.assertTrue(refs.contains(System.identityHashCode(captor5.getAllValues().get(0))));

		refs.clear();

		// second pair
		refs.add(System.identityHashCode(captor1.getAllValues().get(1)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor2.getAllValues().get(1))));
		refs.add(System.identityHashCode(captor2.getAllValues().get(1)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor3.getAllValues().get(1))));
		refs.add(System.identityHashCode(captor3.getAllValues().get(1)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor4.getAllValues().get(1))));
		refs.add(System.identityHashCode(captor4.getAllValues().get(1)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor6.getAllValues().get(1))));
		refs.add(System.identityHashCode(captor6.getAllValues().get(1)));

		Assert.assertTrue(refs.contains(System.identityHashCode(captor5.getAllValues().get(1))));

		refs.clear();

		// third pair
		refs.add(System.identityHashCode(captor1.getAllValues().get(2)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor2.getAllValues().get(2))));
		refs.add(System.identityHashCode(captor2.getAllValues().get(2)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor3.getAllValues().get(2))));
		refs.add(System.identityHashCode(captor3.getAllValues().get(2)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor4.getAllValues().get(2))));
		refs.add(System.identityHashCode(captor4.getAllValues().get(2)));
		Assert.assertFalse(refs.contains(System.identityHashCode(captor6.getAllValues().get(2))));
		refs.add(System.identityHashCode(captor6.getAllValues().get(2)));

		Assert.assertTrue(refs.contains(System.identityHashCode(captor5.getAllValues().get(2))));

		refs.clear();
	}

	private class MockTask extends AbstractTask {

		@Override
		public void invoke() throws Exception {
		}

		@Override
		public void registerInputOutput() {
		}

		@Override
		public Environment getEnvironment() {
			return new MockEnvironment();
		}
	}

	private class MockEnvironment extends Environment {

		@Override
		public int getNumberOfOutputGates() {
			return 0;
		}
	}

}
