package org.unibl.etf.kripto.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.KeyTransRecipientInformation;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

public class BouncyCastleCrypto {

	public static byte[] encryptData(byte[] data, X509Certificate encryptionCertificate, String algorithm) {
		byte[] encryptedData = null;
		if (null != data && null != encryptionCertificate) {
			CMSEnvelopedDataGenerator cmsEnvelopedDataGenerator = new CMSEnvelopedDataGenerator();
			try {
				JceKeyTransRecipientInfoGenerator jceKey = new JceKeyTransRecipientInfoGenerator(encryptionCertificate);
				cmsEnvelopedDataGenerator.addRecipientInfoGenerator(jceKey);
				CMSTypedData msg = new CMSProcessableByteArray(data);
				OutputEncryptor encryptor;
				if (algorithm.equals(Encryption.AES128.toString())) {
					encryptor = new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES128_CBC).setProvider("BC").build();
				} else if (algorithm.equals(Encryption.DES_EDE3.toString())) {
					encryptor = new JceCMSContentEncryptorBuilder(CMSAlgorithm.DES_EDE3_CBC).setProvider("BC").build();
				} else {
					encryptor = new JceCMSContentEncryptorBuilder(CMSAlgorithm.CAMELLIA128_CBC).setProvider("BC")
							.build();
				}
				CMSEnvelopedData cmsEnvelopedData = cmsEnvelopedDataGenerator.generate(msg, encryptor);
				encryptedData = cmsEnvelopedData.getEncoded();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return encryptedData;
	}

	public static byte[] decryptData(final byte[] encryptedData, final PrivateKey decryptionKey) {
		byte[] decryptedData = null;
		if (null != encryptedData && null != decryptionKey) {
			CMSEnvelopedData envelopedData;
			try {
				envelopedData = new CMSEnvelopedData(encryptedData);
				Collection<RecipientInformation> recip = envelopedData.getRecipientInfos().getRecipients();
				KeyTransRecipientInformation recipientInfo = (KeyTransRecipientInformation) recip.iterator().next();
				JceKeyTransRecipient recipient = new JceKeyTransEnvelopedRecipient(decryptionKey);
				decryptedData = recipientInfo.getContent(recipient);
			} catch (CMSException e) {
				e.printStackTrace();
			}

		}
		return decryptedData;
	}

	public static byte[] signData(byte[] data, final X509Certificate signingCertificate, final PrivateKey signingKey,
			String algorithm) {
		byte[] signedMessage = null;
		List<X509Certificate> certList = new ArrayList<X509Certificate>();
		CMSTypedData cmsData = new CMSProcessableByteArray(data);
		certList.add(signingCertificate);
		try {
			Store certs = new JcaCertStore(certList);
			CMSSignedDataGenerator cmsGenerator = new CMSSignedDataGenerator();
			ContentSigner contentSigner;
			if (algorithm.equals(Hash.SHA256.toString())) {
				contentSigner = new JcaContentSignerBuilder("RIPEMD256withRSA").build(signingKey);
			} else if (algorithm.equals(Hash.MD5.toString())) {
				contentSigner = new JcaContentSignerBuilder("MD5withRSA").build(signingKey);
			} else {
				contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(signingKey);
			}
			cmsGenerator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
					new JcaDigestCalculatorProviderBuilder().setProvider("BC").build()).build(contentSigner,
							signingCertificate));
			cmsGenerator.addCertificates(certs);
			CMSSignedData cms = cmsGenerator.generate(cmsData, true);
			signedMessage = cms.getEncoded();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CertificateEncodingException e) {
			e.printStackTrace();
		} catch (OperatorCreationException e) {
			e.printStackTrace();
		} catch (CMSException e) {
			e.printStackTrace();
		}
		return signedMessage;
	}

	public static boolean verifSignData(final byte[] signedData) {
		ByteArrayInputStream bIn = new ByteArrayInputStream(signedData);
		ASN1InputStream aIn = new ASN1InputStream(bIn);
		try {
			CMSSignedData s = new CMSSignedData(ContentInfo.getInstance(aIn.readObject()));
			aIn.close();
			bIn.close();
			Store certs = s.getCertificates();
			SignerInformationStore signers = s.getSignerInfos();
			Collection<SignerInformation> c = signers.getSigners();
			SignerInformation signer = c.iterator().next();
			Collection<X509CertificateHolder> certCollection = certs.getMatches(signer.getSID());
			Iterator<X509CertificateHolder> certIt = certCollection.iterator();
			X509CertificateHolder certHolder = certIt.next();
			boolean verifResult = signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(certHolder));
			if (!verifResult) {
				return false;
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CMSException e) {
			e.printStackTrace();
		} catch (OperatorCreationException e) {
			e.printStackTrace();
		} catch (CertificateException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static byte[] parseSignedData(final byte[] signedData)  {
		byte[] retVal = null;
		ByteArrayInputStream bIn = new ByteArrayInputStream(signedData);
		ASN1InputStream aIn = new ASN1InputStream(bIn);
		try {
		CMSSignedData s = new CMSSignedData(ContentInfo.getInstance(aIn.readObject()));
		CMSTypedData cmsData = s.getSignedContent();
		ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		cmsData.write(bOut);
		retVal = bOut.toByteArray();
		}catch(IOException e) {
			e.printStackTrace();
		} catch (CMSException e) {
			e.printStackTrace();
		}
		return retVal;
	}
	
	public static SecretKey defineKeyForAES(byte[] keyBytes) {
		if(keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
			throw new IllegalArgumentException("keyBytes wrong length for AES key");
		}
		return new SecretKeySpec(keyBytes, "AES");
	}
	
	public static byte[] aesecbEncrypt(SecretKey key, byte[] data) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding", "BC");
			cipher.init(Cipher.ENCRYPT_MODE, key);
			return cipher.doFinal(data);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return null;
	}
	
	public static byte[] aesecbDecryption(SecretKey key, byte[] cipherText) {
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding", "BC");
			cipher.init(Cipher.DECRYPT_MODE, key);
			 return cipher.doFinal(cipherText);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		} 
		 return null;
	}

	
	public static void main(String[] args) {
		
	}
}
