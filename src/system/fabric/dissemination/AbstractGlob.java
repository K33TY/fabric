/**
 * Copyright (C) 2010-2014 Fabric project group, Cornell University
 *
 * This file is part of Fabric.
 *
 * Fabric is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 2 of the License, or (at your option) any later
 * version.
 * 
 * Fabric is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 */
package fabric.dissemination;

import static fabric.common.ONumConstants.EMPTY_LABEL;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

import fabric.common.Crypto;
import fabric.common.FastSerializable;
import fabric.common.exceptions.InternalError;
import fabric.lang.security.Label;
import fabric.lang.security.SecretKeyObject;
import fabric.worker.RemoteStore;
import fabric.worker.Store;
import fabric.worker.Worker;
import fabric.worker.Worker.Code;

/**
 * A glob represents data that has been encrypted and signed by a store.
 */
public abstract class AbstractGlob<Payload extends FastSerializable> implements
    FastSerializable {
  /**
   * The time at which this glob was created. This acts as a version number.
   */
  private final long timestamp;

  /**
   * A pointer to the encryption key. This can be null if the null cipher was
   * used. (This can happen, for example, if the data was public.)
   */
  private final SecretKeyObject keyObject;

  /**
   * The initialization vector for decrypting. Can be null if the null cipher
   * was used. (This can happen, for example, if the data was public.)
   */
  private final byte[] iv;

  /**
   * The encrypted data.
   */
  private final byte[] data;

  /**
   * The signature on the timestamp, keyObject OID, iv, and data.
   */
  private final byte[] signature;

  /**
   * @param label
   *          the label at which this glob is protected.
   * @param signingKey
   *          the key with which to sign the payload.
   * @param payload
   *          the payload to be encrypted.
   */
  protected AbstractGlob(Label label, PrivateKey signingKey, Payload payload) {
    this.timestamp = System.currentTimeMillis();
    this.keyObject = label.keyObject();
    if (keyObject == null) {
      this.iv = null;
    } else {
      this.iv = Crypto.makeIV();
    }

    try {
      // Set up the crypto for encrypting the payload.
      Cipher cipher = makeCipher(keyObject, Cipher.ENCRYPT_MODE, iv);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      CipherOutputStream cos = new CipherOutputStream(bos, cipher);
      DataOutputStream out = new DataOutputStream(cos);

      // Encrypt the payload.
      payload.write(out);

      // Retrieve the encrypted blob.
      out.flush();
      cos.close();
      this.data = bos.toByteArray();

      if (label.$getOnum() == EMPTY_LABEL) {
        this.signature = new byte[0];
      } else {
        // Sign things.
        Signature signer = Crypto.signatureInstance();
        signer.initSign(signingKey);
        updateSignature(signer);
        this.signature = signer.sign();
      }
    } catch (IOException e) {
      throw new InternalError(e);
    } catch (GeneralSecurityException e) {
      throw new InternalError(e);
    }
  }

  /**
   * Updates the given signature with the data to be signed or verified.
   * 
   * @throws SignatureException
   */
  private void updateSignature(Signature sig) throws SignatureException {
    // Update with the timestamp (which acts as the version number).
    sig.update((byte) (timestamp >>> 56));
    sig.update((byte) (timestamp >>> 48));
    sig.update((byte) (timestamp >>> 40));
    sig.update((byte) (timestamp >>> 32));
    sig.update((byte) (timestamp >>> 24));
    sig.update((byte) (timestamp >>> 16));
    sig.update((byte) (timestamp >>> 8));
    sig.update((byte) timestamp);

    // Update with keyObject OID, if non-null.
    if (keyObject != null) {
      try {
        sig.update(keyObject.$getStore().name().getBytes("UTF8"));
      } catch (UnsupportedEncodingException e) {
        throw new InternalError(e);
      }

      long val = keyObject.$getOnum();
      sig.update((byte) (val >>> 56));
      sig.update((byte) (val >>> 48));
      sig.update((byte) (val >>> 40));
      sig.update((byte) (val >>> 32));
      sig.update((byte) (val >>> 24));
      sig.update((byte) (val >>> 16));
      sig.update((byte) (val >>> 8));
      sig.update((byte) val);
    }

    // Update with iv, if non-null.
    if (iv != null) sig.update(iv);

    // Update with data.
    sig.update(data);
  }

  private Cipher makeCipher(final SecretKeyObject keyObject, int opmode,
      byte[] iv) throws GeneralSecurityException {
    byte[] key = null;
    if (keyObject != null) {
      key = Worker.runInSubTransaction(new Code<SecretKey>() {
        @Override
        public SecretKey run() {
          return keyObject.getKey();
        }
      }).getEncoded();
    }

    return Crypto.cipherInstance(opmode, key, iv);
  }

  /**
   * Whether this Glob is older than the given Glob.
   */
  public boolean isOlderThan(AbstractGlob<?> glob) {
    return timestamp < glob.timestamp;
  }

  public void verifySignature(PublicKey key) throws SignatureException,
      InvalidKeyException {
    if (signature.length == 0) {
      // No signature received. Verify that none was required.
      // XXX This is rather inefficient.
      Payload payload = decrypt();
      verifyNoSignatureRequired(payload);
    } else {
      // Check the signature.
      Signature verifier = Crypto.signatureInstance();
      verifier.initVerify(key);
      updateSignature(verifier);
      if (!verifier.verify(signature))
        throw new SignatureException("Failed to verify signature");
    }
  }

  /**
   * Verifies that the given payload does not require a signature.
   * 
   * @throws SignatureException if the payload requires a signature.
   */
  protected abstract void verifyNoSignatureRequired(Payload payload)
      throws SignatureException;

  /**
   * Serializer.
   */
  @Override
  public void write(DataOutput out) throws IOException {
    out.writeLong(timestamp);
    if (keyObject == null) {
      out.writeBoolean(false);
    } else {
      out.writeBoolean(true);
      out.writeUTF(keyObject.$getStore().name());
      out.writeLong(keyObject.$getOnum());
    }

    if (iv == null) {
      out.writeInt(0);
    } else {
      out.writeInt(iv.length);
      out.write(iv);
    }

    out.writeInt(data.length);
    out.write(data);

    out.writeInt(signature.length);
    out.write(signature);
  }

  /**
   * Deserializer.
   */
  protected AbstractGlob(DataInput in) throws IOException {
    this.timestamp = in.readLong();
    if (in.readBoolean()) {
      Store store = Worker.getWorker().getStore(in.readUTF());
      this.keyObject = new SecretKeyObject._Proxy(store, in.readLong());
    } else this.keyObject = null;

    int ivLength = in.readInt();
    if (ivLength > 0) {
      this.iv = new byte[ivLength];
      in.readFully(this.iv);
    } else {
      this.iv = null;
    }

    this.data = new byte[in.readInt()];
    in.readFully(this.data);

    this.signature = new byte[in.readInt()];
    in.readFully(this.signature);
  }

  public Payload decrypt() {
    try {
      Cipher cipher = makeCipher(keyObject, Cipher.DECRYPT_MODE, iv);
      ByteArrayInputStream bis = new ByteArrayInputStream(data);
      DataInputStream in =
          new DataInputStream(new CipherInputStream(bis, cipher));

      // Decrypt the payload.
      return deserializePayload(in);
    } catch (IOException e) {
      throw new InternalError(e);
    } catch (GeneralSecurityException e) {
      throw new InternalError(e);
    }
  }

  /**
   * Deserializes a payload object from the given input.
   */
  protected abstract Payload deserializePayload(DataInput in)
      throws IOException;

  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Updates the worker and dissemination caches with this glob. If the caches
   * do not have entries for this glob, then nothing is changed.
   * 
   * @return true iff either cache was changed.
   */
  public abstract boolean updateCache(Cache dissemCache, RemoteStore store,
      long onum);
}
