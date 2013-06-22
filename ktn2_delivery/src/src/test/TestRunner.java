package test;

import java.util.Iterator;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import test.no.ntnu.fp.net.co.BasicClient;
import test.no.ntnu.fp.net.co.ExceptionsTest;

public class TestRunner {
	public static void main(String[] args) {
		System.out.println("Running JUnit tests...");
		Result result = JUnitCore.runClasses(ExceptionsTest.class, BasicClient.class);
		System.out.println("Ran: " + result.getRunCount() + " tests.");
		System.out.println("Fail ures: " + result.getFailureCount());
		System.out.println("Ignored: " + result.getIgnoreCount());

		if (result.getFailureCount() > 0) {
			System.out.println("Failure messages: ");
			Iterator<Failure> failiures = result.getFailures().iterator();
			while (failiures.hasNext()) {
				System.out.println("\t" + failiures.next().getMessage());
			}
		}
	}
}
