/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2010, 2011, 2012, 2013, 2014, 2016, 2017 Synacor, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.index.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.Pair;
import com.zimbra.cs.index.DBQueryOperation;
import com.zimbra.cs.index.LuceneFields;
import com.zimbra.cs.index.LuceneQueryOperation;
import com.zimbra.cs.index.NoTermQueryOperation;
import com.zimbra.cs.index.QueryOperation;
import com.zimbra.cs.index.solr.SolrUtils;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;

/**
 * Query by text.
 *
 * @author tim
 * @author ysasaki
 */
public class TextQuery extends Query {
    private final String field;
    private final String text;
    private boolean quick = false;
    private boolean isPhraseQuery;
    private final Set<MailItem.Type> types;

    /**
     * A single search term. If text has multiple words, it is treated as a phrase (full exact match required) text may
     * end in a *, which wildcards the last term.
     */
    public TextQuery(String field, String text, boolean isPhraseQuery,  Set<MailItem.Type> types) {
        this.field = field;
        this.text = text;
        this.isPhraseQuery = isPhraseQuery;
        this.types = types;
    }

    public TextQuery(String field, String text, Set<MailItem.Type> types) {
        this(field, text, false, types);
    }

    public TextQuery(String field, String text) {
        this(field, text, false, Collections.emptySet());
    }

    /**
     * Enables quick search.
     * <p>
     * Makes this a wildcard query and gives a query suggestion by auto-completing the last term with the top term,
     * which is the most frequent term among the wildcard-expanded terms.
     *
     * TODO: The current query suggestion implementation can't auto-complete a phrase query as a phrase. It simply
     * auto-completes the last term as if it's a single term query.
     */
    public void setQuick(boolean value) {
        quick = value;
    }

    /**
     * Returns the Lucene field.
     *
     * @see LuceneFields
     * @return lucene field
     */
    public String getField() {
        return field;
    }

    @Override
    public boolean hasTextOperation() {
        return true;
    }

    @Override
    public QueryOperation compile(Mailbox mbox, boolean bool) throws ServiceException {
        if (text.length() == 0) {
            return new NoTermQueryOperation();
        }
        if (LC.search_disable_standalone_wildcard_query.booleanValue()) {
            if (text.equals("*")) {
                if((field.equals(LuceneFields.L_CONTENT) || field.equals(LuceneFields.L_H_SUBJECT)) && evalBool(bool)) {
                    return new DBQueryOperation();
                } else {
                    return new NoTermQueryOperation();
                }
            }
        }
        LuceneQueryOperation op = new LuceneQueryOperation();
        String solrQuery = quick? text + "*": text;
        String queryField;
        String queryString;
        if (solrQuery.contains("*")) {
            if (LC.search_disable_leading_wildcard_query.booleanValue()) {
                solrQuery = stripLeadingWildcards(solrQuery);
            }
            // route to edge n-gram tokenized fields if possible
            Pair<String, String> wildcardQueryInfo = SolrUtils.getWildcardQueryTarget(field, solrQuery);
            queryField = wildcardQueryInfo.getFirst();
            queryString = wildcardQueryInfo.getSecond();
        } else {
            queryField = field;
            queryString = solrQuery;
        }
        org.apache.lucene.search.Query query;
        String[] additionalFields = getAdditionalFields(queryField);
        if (queryString.contains("*")) {
            query = new ZimbraWildcardQuery(queryString, queryField).addFields(additionalFields);
        } else if (additionalFields != null) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(getQuery(queryField, queryString), Occur.SHOULD);
            for (String field: additionalFields) {
                builder.add(getQuery(field, queryString), Occur.SHOULD);
            }
            query = builder.build();
        } else {
            query = getQuery(queryField, queryString);
        }
        op.addClause(toQueryString(field, text), query, evalBool(bool), getIndexTypes(types));
        return op;
    }

    private org.apache.lucene.search.Query getQuery(String field, String queryString) {
        if (isPhraseQuery) {
            return new PhraseQuery(field, queryString);
        } else {
            return new TermQuery(new Term(field, queryString));
        }
    }

    private static String[] getAdditionalFields(String searchField) {
        if (searchField.equals(LuceneFields.L_CONTENT)) {
            return new String[] {
                    LuceneFields.L_H_SUBJECT,
                    SolrUtils.getSearchFieldName(LuceneFields.L_H_TO),
                    SolrUtils.getSearchFieldName(LuceneFields.L_H_FROM),
                    SolrUtils.getSearchFieldName(LuceneFields.L_H_CC),
                    SolrUtils.getSearchFieldName(LuceneFields.L_FILENAME)
            };
        } else {
            return null;
        }
    }

    private String stripLeadingWildcards(String queryStr) {
        if (isPhraseQuery) {
            List<String> parts = new ArrayList<>();
            for (String part: queryStr.split("\\s")) {
                if (part.startsWith("*")) {
                    parts.add(StringUtils.stripStart(part, "*"));
                } else {
                    parts.add(part);
                }
            }
            return Joiner.on(" ").join(parts);
        } else {
            return StringUtils.stripStart(queryStr, "*");
        }
    }

    @Override
    public void dump(StringBuilder out) {
        out.append(field);
        out.append(':');
        out.append(text);
        if (quick && !text.endsWith("*")) {
            out.append("[*]");
        }
    }

    @Override
    public void sanitizedDump(StringBuilder out) {
        int numWordsInQuery = text.split("\\s").length;
        out.append(field);
        out.append(":");
        out.append(Strings.repeat("$TEXT,", numWordsInQuery));
        if (out.charAt(out.length()-1) == ',') {
            out.deleteCharAt(out.length()-1);
        }
        if (quick && !text.endsWith("*")) {
            out.append("[*]");
        }
    }

}
