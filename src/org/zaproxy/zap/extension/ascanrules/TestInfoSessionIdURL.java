/*
 *
 * Paros and its related class files.
 * 
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 * 
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2012/01/02 Separate param and attack
// ZAP: 2012/04/25 Added @Override annotation to all appropriate methods.
// ZAP: 2012/12/28 Issue 447: Include the evidence in the attack field
// ZAP: 2013/01/25 Removed the "(non-Javadoc)" comments.
// ZAP: 2013/03/03 Issue 546: Remove all template Javadoc comments
// ZAP: 2013/07/19 Issue 366: "Other Info" for "Session ID in URL rewrite" not always correct

package org.zaproxy.zap.extension.ascanrules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.parosproxy.paros.core.scanner.AbstractAppPlugin;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Category;
import org.parosproxy.paros.network.HttpMessage;


public class TestInfoSessionIdURL extends AbstractAppPlugin {

	/*
	private static Pattern staticSessionCookieNamePHP = Pattern("PHPSESSID", PATTERN.PARAM);
	private 
	
	ASP = ASPSESSIONIDxxxxx=xxxxxx
	PHP = PHPSESSID
	Cole fusion = CFID, CFTOKEN	(firmed, checked with Macromedia)
	Java (tomcat, jrun, websphere, sunone, weblogic )= JSESSIONID=xxxxx	
	
	*/
	
	private static final String GENERIC_SESSION_TOKEN_VALUE = "\\w+";
	private static final Pattern staticSessionIDPHP1 = Pattern.compile("(PHPSESSION)=" + GENERIC_SESSION_TOKEN_VALUE, PATTERN_PARAM);
	private static final Pattern staticSessionIDPHP2 = Pattern.compile("(PHPSESSID)=" + GENERIC_SESSION_TOKEN_VALUE, PATTERN_PARAM);
	private static final Pattern staticSessionIDJava = Pattern.compile("(JSESSIONID)=[\\w\\-!]+", PATTERN_PARAM);
	private static final Pattern staticSessionIDASP = Pattern.compile("(ASPSESSIONID)=" + GENERIC_SESSION_TOKEN_VALUE, PATTERN_PARAM);
	private static final Pattern staticSessionIDColdFusion = Pattern.compile("(CFTOKEN)=" + GENERIC_SESSION_TOKEN_VALUE, PATTERN_PARAM);
	private static final Pattern staticSessionIDJW = Pattern.compile("(JWSESSIONID)=" + GENERIC_SESSION_TOKEN_VALUE, PATTERN_PARAM);
	private static final Pattern staticSessionIDWebLogic = Pattern.compile("(WebLogicSession)=" + GENERIC_SESSION_TOKEN_VALUE, PATTERN_PARAM);
	private static final Pattern staticSessionIDApache = Pattern.compile("(SESSIONID)=" + GENERIC_SESSION_TOKEN_VALUE, PATTERN_PARAM);
	
	private static final Pattern[] staticSessionIDList =
		{staticSessionIDPHP1, staticSessionIDPHP2, staticSessionIDJava, staticSessionIDColdFusion,
			staticSessionIDASP, staticSessionIDJW, staticSessionIDWebLogic, staticSessionIDApache};

    
    @Override
    public int getId() {
        return 00003;
    }

    @Override
    public String getName() {
        return "Session ID in URL rewrite";
    }



    @Override
    public String[] getDependency() {
        return null;
    }

    @Override
    public String getDescription() {
        return "URL rewrite is used to track user session ID.  The session ID may be disclosed in referer header.  Besides, the session ID can be stored in browser history or server logs.";
    }

    @Override
    public int getCategory() {
        return Category.INFO_GATHER;
    }

    @Override
    public String getSolution() {
        return "For secure content, put session ID in cookie.  To be even more secure consider to use a combination of cookie and URL rewrite.";
    }

    @Override
    public String getReference() {
        return "http://seclists.org/lists/webappsec/2002/Oct-Dec/0111.html";
    }

    @Override
    public void init() {

    }

    @Override
    public void scan() {
        HttpMessage base = getBaseMsg();
        
        String uri = base.getRequestHeader().getURI().toString();
        Matcher matcher = null;
		String sessionIdValue = null;
		String sessionIdName = null;
		for (int i=0; i<staticSessionIDList.length; i++) {
			matcher = staticSessionIDList[i].matcher(uri);
			if (matcher.find()) {
				sessionIdValue = matcher.group(0);
				sessionIdName = matcher.group(1);
				String kb = getKb().getString("sessionId/nameValue");

				if (kb == null || !kb.equals(sessionIdValue)) {
				    getKb().add("sessionId/nameValue", sessionIdValue);
					bingo(Alert.RISK_LOW, Alert.WARNING, uri, null, "", null, sessionIdValue, base);
				}
				kb = getKb().getString("sessionId/name");
				getKb().add("sessionId/name", sessionIdName);
				try {
                    checkSessionIDExposure(base);
                } catch (URIException e) {
                }
				break;
			}
		}
		
	}
    
	private static final String paramHostHttp = "http://([\\w\\.\\-_]+)";
	private static final String paramHostHttps = "https://([\\w\\.\\-_]+)";
	private static final Pattern[] staticLinkCheck = {
		Pattern.compile("src\\s*=\\s*\"?" + paramHostHttp, PATTERN_PARAM),
		Pattern.compile("href\\s*=\\s*\"?" + paramHostHttp, PATTERN_PARAM),
		Pattern.compile("src\\s*=\\s*\"?" + paramHostHttps, PATTERN_PARAM),
		Pattern.compile("href\\s*=\\s*\"?" + paramHostHttps, PATTERN_PARAM),
		
	};

	private static final String alertReferer = "Referer expose session ID";
	private static final String descReferer = "Hyperlink to other host name is found.  As session ID URL rewrite is used, it may be disclosed in referer header to external host.";
	private static final String solutionReferer = "This is a risk if the session ID is sensitive and the hyperlink refer to an external host.  For secure content, put session ID in secured session cookie.";

	private void checkSessionIDExposure(HttpMessage msg) throws URIException {

		String body = msg.getResponseBody().toString();
		int risk = (msg.getRequestHeader().isSecure()) ? Alert.RISK_MEDIUM : Alert.RISK_INFO;
		String linkHostName = null;
		Matcher matcher = null;
		
		for (int i=0; i<staticLinkCheck.length; i++) {
			matcher = staticLinkCheck[i].matcher(body);
		
			while (matcher.find()) {
				linkHostName = matcher.group(1);
				String host = msg.getRequestHeader().getURI().getHost();
				if (host.compareToIgnoreCase(linkHostName) != 0) {
					bingo(risk, Alert.WARNING, alertReferer, descReferer, 
							msg.getRequestHeader().getURI().getURI(), null, "", null, solutionReferer, linkHostName, msg);
				}
			}
		}
	}

	@Override
	public int getRisk() {
		return Alert.RISK_MEDIUM;
	}

	@Override
	public int getCweId() {
		return 200;
	}

	@Override
	public int getWascId() {
		return 13;
	}

}