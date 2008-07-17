<%@ page language="java" import="org.w3c.dom.*, cms.www.*, edu.cornell.csuglab.cms.author.*, cms.www.xml.*, edu.cornell.csuglab.cms.base.*" %><%
 Document displayData = (Document) session.getAttribute(AccessController.A_DISPLAYDATA);
 Element root = (Element) displayData.getChildNodes().item(0);
 Element course = XMLUtil.getFirstChildByTagName(root, XMLBuilder.TAG_COURSE);
 boolean isStudent = course != null ? course.hasAttribute(XMLBuilder.A_ISSTUDENT) : false;
 String courseid = (course != null ? course.getAttribute(XMLBuilder.A_COURSEID) : null);
 String URL = request.getServletPath();
 Principal p = (Principal)session.getAttribute(AccessController.A_PRINCIPAL);
 NodeList allassigns = new CMSNodeList();
 try {
 	Element assigns = (Element) root.getElementsByTagName(XMLBuilder.TAG_ASSIGNMENTS).item(0);
 	if (assigns != null) {
 		allassigns = assigns.getChildNodes();
 	}
 } catch (Exception e) {}
%>

<td class="staff_navigation" rowspan="3" width="224px">
 <table border="0" cellpadding="0" cellspacing="0" width="224px">
 <tr>
  <td width="20px">&nbsp;</td>
  <td id="sidenav" colspan="2" width="170px">
  <ul><%
if (courseid != null) {%>
  <li>
   <span class="course"><%=course.getAttribute(XMLBuilder.A_DISPLAYEDCODE)%></span><br/>
   <span class="semester">(<%=course.getAttribute(XMLBuilder.A_SEMESTER)%>)</span>
  </li>
  <li class="sep"><hr></li>
  <li><a <%= URL.equals(AccessController.COURSE_URL) ? "class=\"currentpage\"" : "" %> href="?<%=AccessController.P_ACTION%>=<%=AccessController.ACT_COURSE%>&amp;<%=AccessController.P_COURSEID%>=<%=courseid%>">
    Home
  </a></li><%
  if (isStudent) {%>
  <li class="menuhead">
  <a <%= (allassigns.getLength() == 0) ? "class=\"unavailable\"" : "" %> href="#">Assignments</a>
	<ul><%
	if (allassigns.getLength() == 0) { %>
	<li>No Assignments.</li><%
	}
	else for (int i = 0; i < allassigns.getLength(); i++)
	{
		Element xAssign = (Element) allassigns.item(i);
		/* it could also be a survey/quiz... Panut needs to commit!  This was crashing me - Alex*/
		if (xAssign.getNodeName().equals(XMLBuilder.A_ASSIGNMENT)) {
			String status = xAssign.getAttribute(XMLBuilder.A_STATUS);
		    boolean isHidden = status.equals(AssignmentBean.HIDDEN);
			int type = Integer.parseInt(xAssign.getAttribute(XMLBuilder.A_ASSIGNTYPE));
			String action = "";
			
			if (type == AssignmentBean.ASSIGNMENT)
				action = AccessController.ACT_ASSIGN;
			else if (type == AssignmentBean.SURVEY)
				action = AccessController.ACT_SURVEY;
			else if (type == AssignmentBean.QUIZ)
				action = AccessController.ACT_QUIZ;
			
			isHidden = status.equals(AssignmentBean.HIDDEN);
			if (!isHidden) {%>
	<li><a href="?<%= AccessController.P_ACTION%>=<%=AccessController.ACT_ASSIGN%>&amp;<%=AccessController.P_ASSIGNID%>=<%=xAssign.getAttribute(XMLBuilder.A_ASSIGNID)%>">
	  &bull; <%= xAssign.getAttribute(XMLBuilder.A_NAME) %>
	</a></li><%
			}
		}
	}%>
	</ul>
  </li>
  <li><a href="?<%=AccessController.P_ACTION%>=<%=AccessController.ACT_STUDENTPREFS%>&amp;<%=AccessController.P_COURSEID%>=<%=courseid%>">
    Notifications
  </a></li>
  <li class="sep"><hr></li><%
  }
}%>
  <li><a href="http://www.cs.cornell.edu/Projects/CMS/help.html" target="_blank">
    Help
  </a></li>
  <li><a href="news://newsstand.cit.cornell.edu/cornell.dept.cs.cms" target="_blank">
    CMS Newsgroup
  </a></li>
  <li><a href="http://www.cs.cornell.edu/Projects/CMS" target="_blank">
    Credits
  </a></li>
  </ul>
  </td>
  <td id="navbar_menu_container" width="14px"><div id="navbar_menu_top">&nbsp;</div></td>
  <td width="20px"> &nbsp;</td>
 </tr>
 <tr><%-- height="14px" was here (why?), but no such attribute - Alex, Oct 2007 --%>
  <td width="20px">&nbsp;</td>
  <td width="61px">
    <div id="navbar_bottom_left">&nbsp;</div>  
  </td>
  <td width="123px"><%-- 
  Without widths, gaps appear for IE
  This one was 109 (sum used to = 224 which is as expected; 123 is 14px too much)
  Don't know why width changed; any way to make this auto? - Alex, Oct 2007 --%>
    <div id="navbar_bottom">&nbsp;</div>  
  </td>
  <td width="14px">
    <div id="navbar_menu_bottom">&nbsp;</div>
  </td>
  <td width="20px">&nbsp;</td>
 </tr>
 </table>
</td>