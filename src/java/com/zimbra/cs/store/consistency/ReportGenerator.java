/*
 * ***** BEGIN LICENSE BLOCK *****
 * 
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2008 Zimbra, Inc.
 * 
 * The contents of this file are subject to the Yahoo! Public License
 * Version 1.0 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.store.consistency;

import java.io.File;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


public class ReportGenerator implements Runnable {

    private final String mysqlPasswd;
    private final File reportFile;
    private final static String JDBC_URL = "jdbc:mysql://localhost:7306/";
    /**
     * type IN (store, secondary-store).
     */
    private final static String VOLUME_QUERY =
            "SELECT id, path, file_bits, file_group_bits," +
            " mailbox_bits, mailbox_group_bits, compress_blobs" +
            " FROM volume WHERE type IN (1,2)";
    /**
     * type IN (message, appointment, task).
     */
    private final static String ITEM_QUERY =
            "SELECT id, type, i.mailbox_id," +
            " i.volume_id, i.size, i.blob_digest, i.mod_content," +
            " r.version, r.size, r.volume_id, r.blob_digest, r.mod_content" +
            " FROM mail_item i LEFT OUTER JOIN revision r" +
            " ON i.id = r.item_id AND i.mailbox_id = r.mailbox_id" +
            " WHERE type IN (5, 11, 15)";

    public ReportGenerator(String mysqlPasswd, File reportFile) {
        this.mysqlPasswd = mysqlPasswd;
        this.reportFile = reportFile;
    }

    public void run() {
        ArrayList<ItemFault> faults = new ArrayList<ItemFault>();
        Map<Byte,Volume> volumes = null;
        try {
            Connection c = null;
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            File tmpFile = null;
            try {
                c = DriverManager.getConnection(JDBC_URL + "zimbra",
                        BlobConsistencyCheck.ZIMBRA_USER, mysqlPasswd);
                StatementExecutor e = new StatementExecutor(c);
                volumes = getVolumeInfo(e);
                List<Integer> mboxGroups = getMailboxGroupList(e);
                c.close();

                tmpFile = File.createTempFile("mailitems", ".lst");
                out = new ObjectOutputStream(
                        new FileOutputStream(tmpFile, true));
                System.out.println("Spooling item list to " + tmpFile);
                int items = 0;
                for (int group : mboxGroups) {
                    String mboxgroup = "mboxgroup" + group;
                    System.out.println("Retrieving items from " + mboxgroup);
                    c = DriverManager.getConnection(JDBC_URL + mboxgroup,
                        BlobConsistencyCheck.ZIMBRA_USER, mysqlPasswd);
                    e = new StatementExecutor(c);
                    items += getMailItems(group, e, out);
                    c.close();
                }
                out.close();

                long start = System.currentTimeMillis();
                in = new ObjectInputStream(
                        new FileInputStream(tmpFile));

                try {
                    for (int i = 0; i < items; i++) {
                        Object o = in.readObject();
                        Item item = (Item) o;

                        // TODO also check when a file exists without metadata
                        Volume v = volumes.get(item.volumeId);
                        File f = v.getItemFile(item);
                        if (!f.exists()) {
                            boolean found = false;
                            byte foundId = 0;
                            for (Volume vol : volumes.values()) {
                                f = vol.getItemFile(item);
                                if (f.exists()) {
                                    found = true;
                                    foundId = vol.id;
                                }
                            }
                            if (found) {
                                faults.add(new ItemFault(item, item, null,
                                        ItemFault.Code.WRONG_VOLUME,
                                        foundId, 0));
                            } else {
                                faults.add(new ItemFault(item, item, null,
                                        ItemFault.Code.NOT_FOUND,
                                        (byte) 0, 0));
                            }
                        } else if (f.length() != item.size && !v.compressed) {
                            faults.add(new ItemFault(item, item, null,
                                    ItemFault.Code.WRONG_SIZE,
                                    (byte) 0, f.length()));
                        }

                        for (Item.Revision rev : item.revisions) {
                            v = volumes.get(rev.volumeId);
                            f = v.getItemRevisionFile(item, rev);
                            if (!f.exists()) {
                                boolean found = false;
                                byte foundId = 0;
                                for (Volume vol : volumes.values()) {
                                    f = vol.getItemRevisionFile(item, rev);
                                    if (f.exists()) {
                                        found = true;
                                        foundId = vol.id;
                                    }
                                }
                                if (found) {
                                    faults.add(new ItemFault(item, null, rev,
                                            ItemFault.Code.WRONG_VOLUME,
                                            foundId, 0));
                                } else {
                                    faults.add(new ItemFault(item, null, rev,
                                            ItemFault.Code.NOT_FOUND,
                                            (byte) 0, 0));
                                }
                            } else if (f.length() != item.size
                                    && !v.compressed) {
                                faults.add(new ItemFault(item, null, rev,
                                        ItemFault.Code.WRONG_SIZE,
                                        (byte) 0, f.length()));
                            }
                        }
                    }
                }
                catch (ClassNotFoundException ex) {
                    IOException ioe = new IOException();
                    ioe.initCause(ex);
                    throw ioe;
                }
                System.out.println(tmpFile + ": size " + tmpFile.length());
                long elapsed = System.currentTimeMillis() - start;
                System.out.printf("Processed %d items in %dms\n",
                        items, elapsed);
            }
            finally {
                if (c   != null) c.close();
                if (out != null) out.close();
                if (in  != null) in.close();

                if (tmpFile != null) tmpFile.delete();
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        if (faults.size() == 0) {
            reportFile.delete();
            System.out.println("No inconsistencies found");
        } else {
            String inconsistency =
                    faults.size() == 1 ? " inconsistency" : " inconsistencies";
            System.out.println(faults.size() + inconsistency + " found");
            try {
                ObjectOutputStream oos = null;
                try {
                    oos = new ObjectOutputStream(
                        new FileOutputStream(reportFile));
                    oos.writeObject(volumes);
                    oos.writeObject(faults);
                }
                finally {
                    if (oos != null) oos.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            new ReportDisplay(reportFile).run();
            System.out.println("Result report saved to: " + reportFile);
        }
    }

    private Map<Byte,Volume> getVolumeInfo(StatementExecutor e)
    throws SQLException {
        
        System.out.println("Retrieving volume information");
        final HashMap<Byte,Volume> m = new HashMap<Byte,Volume>();
        e.query(VOLUME_QUERY, new StatementExecutor.ObjectMapper() {
            public void mapRow(ResultSet rs) throws SQLException {
                byte id = rs.getByte("id");
                Volume v = new Volume(id,
                        rs.getString("path"),
                        rs.getShort("file_bits"),
                        rs.getShort("file_group_bits"),
                        rs.getShort("mailbox_bits"),
                        rs.getShort("mailbox_group_bits"),
                        rs.getBoolean("compress_blobs"));
                m.put(id, v);
            }
        });
        return m;
    }

    private List<Integer> getMailboxGroupList(StatementExecutor e)
    throws SQLException {
        System.out.println("Retrieving mboxgroup list");
        final ArrayList<Integer> l = new ArrayList<Integer>();
        e.query("SHOW DATABASES", new StatementExecutor.ObjectMapper() {
            public void mapRow(ResultSet rs) throws SQLException {
                String name = rs.getString(1);
                // add number only
                if (name != null && name.startsWith("mboxgroup"))
                    l.add(Integer.parseInt(name.substring(9)));
            }
        });
        Collections.sort(l);
        return l;
    }

    private int getMailItems(final int group,
            StatementExecutor e, final ObjectOutputStream oos)
    throws SQLException, IOException {
        // lazy, fake pointer
        final int[] countref = new int[1];
        e.query(ITEM_QUERY, new StatementExecutor.ObjectMapper() {
            private Item lastItem = null;
            private void serialize(Object o) throws SQLException {
                try {
                    oos.writeObject(o);
                    // help avoid OOME
                    if (countref[0] % 10000 == 0)
                        oos.reset();
                    countref[0]++;
                }
                catch (IOException e) {
                    SQLException x = new SQLException();
                    x.initCause(e);
                    throw x;
                }
            }

            public void mapRow(ResultSet rs) throws SQLException {
                int id = rs.getInt("id");
                int mailboxId = rs.getInt("i.mailbox_id");
                if (lastItem != null &&
                        lastItem.id == id && lastItem.mailboxId == mailboxId) {
                    Item.Revision rev = new Item.Revision(
                            rs.getInt("r.version"),
                            rs.getByte("r.volume_id"),
                            rs.getLong("r.size"),
                            rs.getString("r.blob_digest"),
                            rs.getInt("r.mod_content"));
                    lastItem.revisions.add(rev);
                } else if (lastItem != null && lastItem.id != id) {
                    // new item; serialize previous
                    serialize(lastItem);

                    Item item = new Item(id, group, mailboxId,
                            rs.getByte("type"),
                            rs.getByte("i.volume_id"),
                            rs.getLong("i.size"),
                            rs.getString("i.blob_digest"),
                            rs.getInt("i.mod_content"));
                    lastItem = item;
                } else {
                    // first item
                    Item item = new Item(id, group, mailboxId,
                            rs.getByte("type"),
                            rs.getByte("i.volume_id"),
                            rs.getLong("i.size"),
                            rs.getString("i.blob_digest"),
                            rs.getInt("i.mod_content"));
                    lastItem = item;
                }

                if (rs.isLast())
                    serialize(lastItem);

            }
        });
        return countref[0];
    }

}
