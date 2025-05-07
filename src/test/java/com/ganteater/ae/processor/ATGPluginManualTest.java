package com.ganteater.ae.processor;

import org.junit.Before;
import org.junit.Test;

import com.ganteater.ae.RecipeRunner;

public class ATGPluginManualTest {

	@Before
	public void configuration() {
	}

	@Test
	public void testGUI() throws Exception {
		RecipeRunner.main();
		System.in.read();
	}

}
