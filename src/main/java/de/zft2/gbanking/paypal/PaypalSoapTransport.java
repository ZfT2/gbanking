package de.zft2.gbanking.paypal;

import java.net.URI;

@FunctionalInterface
interface PaypalSoapTransport {

	String send(URI endpoint, String requestXml) throws InterruptedException;
}
