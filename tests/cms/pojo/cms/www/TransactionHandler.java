/*
 * Created on Jul 19, 2004
 */
package cms.www;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.rmi.RemoteException;
import java.util.Date;
import java.text.ParseException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;

import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
// import org.apache.crimson.jaxp.DocumentBuilderFactoryImpl;
import javax.xml.parsers.DocumentBuilderFactory; // java 5 replacement for
                                                  // DocumentBuilderFactoryImpl

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import cms.www.util.CSVFileFormatsUtil;
import cms.www.util.CSVParseException;
import cms.www.util.DateTimeUtil;
import cms.www.util.DownloadFile;
import cms.www.util.Emailer;
import cms.www.util.Profiler;
import cms.www.util.StringUtil;
import cms.www.util.Util;
import cms.www.xml.XMLBuilder;
import cms.www.xml.XMLUtil;

import com.Ostermiller.util.CSVParse;
import com.Ostermiller.util.CSVPrinter;
import com.Ostermiller.util.ExcelCSVParser;
import com.Ostermiller.util.ExcelCSVPrinter;

import cms.model.*;

/**
 * TransactionHandler is a wrapper class for Transaction. For each
 * transaction the system supports, the servlet passes the transaction
 * parameters to the methods in this class, the validity of the arguments is
 * verified, and meaningful error messages are produced to be displayed to the
 * end user on any sort of transaction failure.
 * 
 * @author Jon
 */
public class TransactionHandler {
  private CMSRoot      database     = null;
  private Transactions transactions = null;
  private DocumentBuilder db = null;
  private Properties env;
  
  private static class UploadTooBigException extends IOException {
    public UploadTooBigException() {
      super("Upload exceeds maximum file size");
    }
  }

  public TransactionHandler(final CMSRoot database) {
    this.database     = database;
    this.transactions = new Transactions(database); 
  }

  /**
   * Accepts an invitation to a user. Has the side effect of rejecting any other
   * invitations for the same assignment, as well as removing the user from any
   * other groups they are currently active in.
   * 
   * @param netid
   *          The Net of the user
   * @param groupid
   *          The Group of the group to accept into
   * @return TransactionResult
   */
  public TransactionResult acceptInvitation(User p, Group group) {
    TransactionResult result = new TransactionResult();
    Assignment assignment = group.getAssignment();
    Student student = assignment == null ? null
        : assignment.getCourse().getStudent(p);
    GroupMember member  = group.findGroupMember(p);
    GroupMember current = assignment.findActiveGroupMember(p);
    if (group == null) {
      result.addError("Invalid group entered, does not exist");
      return result;
    }
    if (assignment == null || assignment.getHidden()) {
      result.addError("No corresponding assignment exists");
      return result;
    } else if (!assignment.getStatus().equals(Assignment.OPEN)) {
      result.addError("Group management is not currently available for this assignment");
      return result;
    }
    if (courseIsFrozen(assignment.getCourse())) {
      result.addError("Course is frozen, no changes may be made to it");
      return result;
    }
    if (student == null || !student.getStatus().equals(Student.ENROLLED)) {
      result.addError("Must be an enrolled student in this course to accept invitations");
      return result;
    }

    if (member == null || !member.getStatus().equals(GroupMember.INVITED)) {
      result.addError("No invitation to join this group exists");
    }
    if (member != null && member.getStatus().equals(GroupMember.ACTIVE)) {
      result.addError("Already an active member of this group");
    }
    Collection grades = current == null ? null
                                        : assignment.findMostRecentGrades(p);
    if (grades != null && grades.size() > 0) {
      result.addError("Cannot change groups for this assignment");
    }
    if (!result.hasErrors()) {
      int numMembers = group.findActiveMembers().size();
      int maxSize = assignment.getGroupSizeMax();
      if (numMembers >= maxSize) {
        result.addError("Cannot join group, it is already full");
      }
      if (!result.hasErrors()) {
        if (transactions.acceptInvitation(p, group)) {
          result.setValue("Successfully joined group");
        } else {
          result.addError("Database failed to update invite acceptance");
        }
      }
    }
    return result;
  }

  /**
   * Add a current user's Net to the list of those given CMS admin access
   * 
   * @return TransactionResult
   */
  public TransactionResult addCMSAdmin(User p, User admin) {
    TransactionResult result = new TransactionResult();
    boolean success = false;
    try {
      success = transactions.addCMSAdmin(p, admin);
    } catch (Exception e) {
      result.addError("Database failed to add CMS admin");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Create a course under the current semester
   * 
   * @param courseCode
   *          The department and number, e.g. COM S 211
   * @param courseName
   *          The course title, e.g. Intro to Java
   * @return TransactionResult
   */
  public TransactionResult addCourse(User p, String courseCode,
      String courseName) {
    TransactionResult result = new TransactionResult();
    boolean success = false;
    try {
      success = transactions.createCourse(p, courseCode, courseName);
    } catch (Exception e) {
      result.addError("Database failed to create course");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Adds a sidewide notice to the database
   * 
   * @param text
   *          The text of the notice
   * @param author
   *          The author of the notice
   * @param exp
   *          The expiration time of the notice (may be null)
   * @param hidden
   *          Whether or not the annoucement is viewable by all
   * @return TransactionResult
   */
  public TransactionResult addNotice(User p, String text, User author,
      Date exp, boolean hidden) {
    TransactionResult result = new TransactionResult();
    boolean success = false;
    try {
      success = transactions.addSiteNotice(p, text, author, exp, hidden);
    } catch (Exception e) {
      result.addError("Database failed to create notice");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Edits a sitewide notice
   * 
   * @param p
   *          The principal of the user
   * @param id
   *          The id of the notice
   * @param text
   *          The text of the notice
   * @param exp
   *          The expiration time of the notice (may be null)
   * @param hidden
   *          Whether the notice should be hidden
   * @param deleted
   *          Whether the notice should be marked as deleteds
   * @return TransactionResult
   * @ejb.interface-method view-type="local"
   * @ejb.transaction type="Required"
   */
  public TransactionResult editNotice(User p, SiteNotice id, String text,
      Date exp, boolean hidden, boolean deleted) {
    TransactionResult result = new TransactionResult();
    boolean success = false;
    try {
      success = transactions.editSiteNotice(p, id, text, exp, hidden, deleted);
    } catch (Exception e) {
      result.addError("Database failed to edit notice");
      e.printStackTrace();
    }
    return result;
  }

  private FileData downloadFile(FileItemStream item) throws IOException {
    String fileName = item.getName();
    // strip off any directory names that may have been sent by the browser
    fileName = fileName.substring(fileName.lastIndexOf("/"));
    fileName = fileName.substring(fileName.lastIndexOf("\\"));
    
    FileData result = new FileData(fileName);
    
    InputStream  in  = item.openStream();
    OutputStream out = result.write();
    Streams.copy(in, out, true);
    out.close();
    
    
    return result;
  }
  
  /**
   * Reads the form field data from a FileItemStream. Similar to
   * FileItem.getString().
   * 
   * @return a String containing the value of the item if it is a simple form
   *         field, or null if it is a file upload.
   * @throws IOException if the FileItemStream can't be read for any reason.
   */
  private String getString(FileItemStream item) throws IOException {
    return Streams.asString(item.openStream());
  }

  /**
   * Helper method for keeping track of created rows. Returns
   * created.get(key), creating it if it doesn't exist.
   */
  private CategoryRow getNewRow(Map/*String, Row*/ created, String key, Category parent) {
    CategoryRow result = (CategoryRow) created.get(key);
    if (result == null) {
      result = null; // TODO: new CategoryRow(parent);
      created.put(key, result);
    }
    return result;
  }

/**
   * Put together a CategoryCtntsOption structure describing all changes to make
   * to the given category, and pass it to Transactions for actual DB
   * changes. Space efficiency in the database: When a cell is created it isn't
   * entered into the database because there's no data in it yet. It is editable
   * from the content-edit page; when non-empty data is entered there for the
   * first time, a database entry is created. We don't remove that entry if the
   * data is subsequently deleted, because this seems very unlikely to happen in
   * practice.
   * 
   * @return TransactionResult
   */
  public TransactionResult addNEditCtgContents(User p, Category cat, HttpServletRequest request) {
    Profiler.enterMethod("TransactionHandler.addNEditCtgContents", "Category: " + cat.toString());
    TransactionResult result = new TransactionResult();
    try {
      if (cat == null)
        result.addError("Couldn't find content in database");
      else if (courseIsFrozen(cat.getCourse()))
        result.addError("Course is frozen; no changes may be made to it");
      else {
        ServletFileUpload upload = new ServletFileUpload();
        /*
         * **************************************************************************
         * format of add request param: <TYPE>_<ROW>_<COL>[_<FILE_INDEX>]
         * **************************************************************************
         * format of edit request param:
         *    <TYPE>_<CONTENT>[_<FILE_INDEX>]   to change data or download a file
         * OR <TYPE>_<ROW>_<COL>[_<FILE_INDEX>] to change data that isn't in the
         *                                      db yet because its cell is
         *                                      currently empty
         * OR <TYPE>_<ROW> or <TYPE>_<FILE>     to hide/unhide a row or file
         * **************************************************************************
         * <COL>        is the column  in the database;
         * <ROW>        is a unique row just for the HTML form
         * <CONTENT>    is the content  in the database
         * <ROW>        is the row  in the database (not generated by the JSP, as
         *              when adding content)
         * <FILE_INDEX> is the index of a file within its content cell
         */
        FileItemIterator i = upload.getItemIterator(request);
        while (i.hasNext()) {
          FileItemStream item = i.next();
          String[] msgParts = item.getFieldName().split("_");
          String   type     = msgParts[0];
          String   value    = getString(item);

          
          CategoryRow      row = null;
          CategoryColumn   col = null;
          CategoryContents contents = null;
          
          //
          // parse request to determine the row, column, and contents
          //
          if (type.equals(AccessController.P_NEW_CONTENT_FILE)      ||
              type.equals(AccessController.P_NEW_CONTENT_FILELABEL) ||
              type.equals(AccessController.P_CONTENT_FILE)          ||
              type.equals(AccessController.P_CONTENT_FILELABEL)) {
            if (msgParts.length == 4) {
              // request is of the form <TYPE>_<ROW>_<COL>_<FILE>
              row = database.getCategoryRow(msgParts[1]);
              col = database.getCategoryColumn(msgParts[2]);
              contents = row.getContents(col);
            } else if (msgParts.length == 3) {
              // request is of the form <TYPE>_<CONTENTS>_<FILE>
              contents = database.getCategoryContents(msgParts[1]);
              row = contents.getRow();
              col = contents.getColumn();
            } else {
              throw new Exception("Invalid file request");
            }
          } else if (type.equals(AccessController.P_REMOVEROW)   ||
                     type.equals(AccessController.P_RESTOREROW) ||
                     type.equals(AccessController.P_REMOVEFILE)) {
            // request is of the form <TYPE>_<ID>, to be parsed below
          } else {
            if (msgParts.length == 3) {
              // request is of the form <TYPE>_<ROW>_<COL>
              row = database.getCategoryRow(msgParts[1]);
              col = database.getCategoryColumn(msgParts[2]);
              contents = row.getContents(col);
            } else if (msgParts.length == 2) {
              // request is of the form <TYPE>_<CONTENTS>
              contents = database.getCategoryContents(msgParts[1]);
              row = contents.getRow();
              col = contents.getColumn();
            }
          }
          
          if (contents == null) {
            contents = row.createContents(col);
          }
          
          //
          // perform updates
          //
          if (type.equals(AccessController.P_NEW_CONTENT_DATE) ||
              type.equals(AccessController.P_CONTENT_DATE)) {
            String dateStr = value;
            Date date = dateStr.equals("") ? null
                                           : DateTimeUtil.parseDate(dateStr, DateTimeUtil.DATE);
            ((CategoryContentsDate) contents).setDate(date);
          } else if (type.equals(AccessController.P_NEW_CONTENT_FILE) ||
                     type.equals(AccessController.P_CONTENT_FILE)) {
            // TODO: download file and put it in entry
            FileData file = downloadFile(item);
          } else if (type.equals(AccessController.P_NEW_CONTENT_FILELABEL) ||
                     type.equals(AccessController.P_CONTENT_FILELABEL)) {
            // TODO: find file entry and update the label
          } else if (type.equals(AccessController.P_NEW_CONTENT_NUMBER) ||
                     type.equals(AccessController.P_CONTENT_NUMBER)) {
            ((CategoryContentsNumber) contents).setValue(Integer.parseInt(value));
          } else if (type.equals(AccessController.P_NEW_CONTENT_TEXT) ||
                     type.equals(AccessController.P_CONTENT_TEXT)) {
            ((CategoryContentsString) contents).setText(value);
          } else if (type.equals(AccessController.P_NEW_CONTENT_URLADDRESS) ||
                     type.equals(AccessController.P_CONTENT_URLADDRESS)) {
            ((CategoryContentsLink) contents).setAddress(value);
          } else if (type.equals(AccessController.P_NEW_CONTENT_URLADDRESS) ||
                     type.equals(AccessController.P_CONTENT_URLLABEL)) {
            ((CategoryContentsLink) contents).setLabel(value);
          } else if (type.equals(AccessController.P_REMOVEROW)) {
            row = database.getCategoryRow(msgParts[1]);
            row.setHidden(true);
          } else if (type.equals(AccessController.P_RESTOREROW)) {
            row = database.getCategoryRow(msgParts[1]);
            row.setHidden(false);
          } else if (type.equals(AccessController.P_REMOVEFILE)) {
            // TODO: remove file entry
          } else {
            // TODO: error? do nothing?
          }
        }
      }
    } catch (UploadTooBigException e) {
      result.addError(e.getMessage(), e);
    } catch (ParseException e) {
      result.addError("Error: Date contents must be of the form MMMM DD, YYYY");
    } catch (Exception e) {
      result.addError("Unexpected error while trying to add/edit data", e);
    }
    Profiler.exitMethod("TransactionHandler.addNEditCtgContents",
        "Category: " + cat.toString());
    return result;
  }

  /**
   * Sets grades and comments for a course. Also accepts submitted files and
   * regrade requests entered by staff members on behalf of a group.
   * 
   * @param p
   * @param isAssign
   *          True if this transaction request comes from the single Assignment
   *          Grade page as opposed to the setting grades for a student in all
   *          assignments page.
   * @param 
   *          If isAssign is true, an assignment ; else a course 
   * @param request
   * @return TransactionResult containing with success set false and with
   *         appended errors if the transaction failed. If it completed
   *         successfully and isAssign is true, then success is true, and the
   *         value object of the TransactionResult is set to a length 2 object
   *         array containing the success message, followed by a List of
   *         Groups (for reloading the page). If the transaction was
   *         successful and isAssign is false, the TransactionResult value is
   *         set to just the success message.
   */
  public TransactionResult addGradesComments(User p, boolean isAssign,
      Object data, HttpServletRequest request) {
    Profiler.enterMethod("TransactionHandler.addGradesComments", "");
    TransactionResult result = new TransactionResult();
    try {
      ServletFileUpload upload = new ServletFileUpload();
      FileItemIterator i = upload.getItemIterator(request);
      
      Assignment assignment = isAssign ? (Assignment) data : null;
      Course     course     = isAssign ? assignment.getCourse() : (Course) data;
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen, cannot make changes");
        return result;
      }
      
      ArrayList groups = new ArrayList();
      while (i.hasNext()) {
        FileItemStream item = (FileItemStream) i.next();
        String field = item.getFieldName();
        String value = getString(item);
        if (field.startsWith(AccessController.P_GRADE)) {
          String[] vals = field.split("_");
          SubProblem subProb = database.getSubProblem(vals[2]);
          Group      group   = database.getGroup(vals[3]);
          try {
            String scoreStr = value.trim();
            if (!scoreStr.equals("")) {
              float score = Float.parseFloat(scoreStr);
              gradeInfo.addScore(vals[1], subProb, new Float(score), group);
            } else
              gradeInfo.addScore(vals[1], subProb, null, group);
          } catch (NumberFormatException e) {
            result.addError("Grade for '" + vals[1] + "' on problem '"
                + subProb.getSubProblemName()
                + "' is not a valid floating point number.");
          }
        } else if (field.startsWith(AccessController.P_OLDGRADE)) {
          String[] vals = field.split("_");
          SubProblem subProb = database.getSubProblem(vals[2]);
          Group      group   = database.getGroup(vals[3]);
          try {
            String scoreStr = value.trim();
            if (!scoreStr.equals("")) {
              float score = StringUtil.parseFloat(scoreStr);
              gradeInfo.addOldScore(vals[1], subProb, score, group);
            }
          } catch (NumberFormatException e) {
            /*
             * This shouldn't happen as P_OLDGRADE represents the score from the
             * database as of the time the user loaded the web page. We should
             * certainly know about it if it happens to though.
             */
            e.printStackTrace();
            result.addError("An unexpected error occurred and grades could not be committed.  Contact CMS staff.");
          }
        } else if (field.startsWith(AccessController.P_COMMENTTEXT)) {
          Group group =
            database.getGroup(field.split(AccessController.P_COMMENTTEXT)[1]);
          gradeInfo.addCommentText(group, value);
        } else if (field.startsWith(AccessController.P_COMMENTFILE)) {
          String fileName = FileUtil.trimFilePath(item.getName());
          if (fileName.equals("")) {
            continue;
          }
          Group group =
              database.getGroup(field.split(AccessController.P_COMMENTFILE)[1]);
          int fileCounter = transactions.getGroupFileCounter(group);
          Assignment assign = isAssign ? assignment : group.getAssignment();
          FileData file = downloadFile(item); 
          data.addCommentFile(group, new CommentFile(0, 0, fileName, path
              .getAbsolutePath()));
        } else if (field.startsWith(AccessController.P_SUBMITTEDFILE)) {
          String[] vals = field.split("_");
          Group group = database.getGroup(vals[1]);
          RequiredSubmission submission = database.getRequiredSubmission(vals[2]);
          String fileName    = FileUtil.trimFilePath(item.getName());
          String extension   = fileName.substring(fileName.lastIndexOf("."));
          if (fileName.equals("")) {
            continue;
          }
          Assignment assign = isAssign ? assignment : group.getAssignment();
          FileData file = downloadFile(item);
          group.addSubmittedFile(new SubmittedFile(group, group, p, submission, extension,
              false, null/* date */, file));
        } else if (field.startsWith(AccessController.P_REGRADERESPONSE)) {
          String[] vals = field.split("_");
          RegradeRequest regrade = database.getRegradeRequest(vals[1]);
          Group group = database.getGroup(vals[2]);
          data.addRegradeResponse(group, request);
        } else if (field.startsWith(AccessController.P_REGRADESUB)) {
          String[] vals = field.split("_");
          long subProb = Long.parseLong(vals[1]);
          Group group = database.getGroup(vals[2]);
          data.addNewRegradeSubProb(group, subProb);
        } else if (field.startsWith(AccessController.P_REGRADEWHOLE)) {
          Group group = database.getGroup(field.split("_")[1]);
          data.addNewRegradeSubProb(group, 0);
        } else if (field.startsWith(AccessController.P_REGRADEREQUEST)) {
          Group group = database.getGroup(field.split("_")[1]);
          data.addNewRegrade(group, value);
        } else if (field.startsWith(AccessController.P_REGRADENETID)) {
          Group group = database.getGroup((field.split("_"))[1]);
          data.addNewRegradeNet(group, value);
        } else if (field.startsWith(AccessController.P_GROUPID)) {
          groups.add(new Long(database.getGroup(value)));
        } else if (field.startsWith(AccessController.P_REMOVECOMMENT)) {
          long comment =
              Long.parseLong(field.substring(AccessController.P_REMOVECOMMENT
                  .length()));
          data.addRemovedComment(comment);
        }
      }
      if (isAssign) result.setValue(new Object[] { null, groups });
      /*
       * If this is an Assignment-level transaction and the assignment is set to
       * assigned graders only, we must check that the grader is not setting
       * anything he's not allowed to. (This shouldn't be possible, since the
       * form elements the grader doesn't have permission for should be disabled
       * or missing, but just in case)
       */
      if (isAssign && !p.isAdminPrivByCourse(assignment.getCourse())) {
        if (assign.getAssignedGraders()) {
          boolean permission =
              database.assignedToGroups(data, p.getNet(), groups).size() == groups.size();
          HashSet canGrades = new HashSet();
          Iterator assignTos =
              database.groupAssignedToHome().findByNetAssignment(
                  p.getNet(), data).iterator();
          while (assignTos.hasNext()) {
            GroupAssignedTo a = (GroupAssignedTo) assignTos.next();
            canGrades.add(a.getGroup() + "_" + a.getSubProblem());
          }
          for (int j = 0; j < data.getScores().size(); j++) {
            Object[] grade = (Object[]) data.getScores().get(j);
            Long groupid = (Long) grade[3];
            Long subprobid = (Long) grade[1];
            permission =
                permission && canGrades.contains(groupid + "_" + subprobid);
          }
          if (!permission) {
            result.addError("Permission violation: You are not allowed to set these grades");
          }
        }
      }
      if (!result.hasErrors()) {
        if (isAssign) {
          result = transactions.addGradesComments(p, data, data);
          // If commit went through successfully and updates were made, update
          // statistics
          if (result.getSuccess()
              && ((Boolean) result.getValue()).booleanValue()) {
            try {
              transactions.computeAssignmentStats(p, data, (LogData) null);
              transactions.computeTotalScores(p, assignment.getCourse(),
                  (LogData) null);
            } catch (Exception e) {
              e.printStackTrace();
              result
                  .addError("Grades committed, but failed to compute updated statistics");
            }
          }
        } else {
          result = transactions.addAllAssignsGrades(p, data, data);
          if (result.getSuccess()) {
            try {
              Iterator assigns = (Iterator) result.getValue();
              while (assigns.hasNext()) {
                Long assign = (Long) assigns.next();
                transactions.computeAssignmentStats(p, assign.longValue(),
                    (LogData) null);
              }
              transactions.computeTotalScores(p, data, (LogData) null);
            } catch (Exception e) {
              e.printStackTrace();
              result
                  .addError("Grades committed, but failed to compute updated statistics");
            }
          }
        }
        String msg =
            result.getSuccess() ? "Grades/comments updated successfully"
                : "Database did not update grades and comments";
        if (isAssign) {
          Object[] pack = new Object[2];
          pack[0] = msg;
          pack[1] = groups;
          result.setValue(pack);
        } else {
          result.setValue(msg);
        }
      } else {
        result.addError("Database did not update grades and comments");
      }
    } catch (FileUploadException e) {
      result.addError(FileUtil.checkFileException(e));
    } catch (Exception e) {
      result.addError("Database did not update grades and comments");
      e.printStackTrace();
    }
    Profiler.exitMethod("TransactionHandler.addGradesComments", "");
    return result;
  }

  /**
   * Submits a regrade request for a student in specified group
   * 
   * @param group -
   *           of the group
   * @param assign -
   *           of assignment regrade if for
   * @param net -
   *           of student submitting the regrade
   * @param request
   * @return TransactionResult
   */
  public TransactionResult addRegradeRequest(User p, Group group,
      HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    boolean success;
    try {
      Assignment assignment = group.getAssignment();
      if (courseIsFrozen(assignment.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      Collection subProblems = new ArrayList();
      String requestText = null;
      for (Enumeration e = request.getParameterNames(); e.hasMoreElements();) {
        String parameter = (String) e.nextElement();
        if (parameter.startsWith(AccessController.P_REGRADESUB)) {
          String subProb = parameter.split(AccessController.P_REGRADESUB)[1];
          subProblems.add(database.getSubProblem(subProb));
        } else if (parameter.equals(AccessController.P_REGRADEREQUEST)) {
          requestText = request.getParameter(parameter);
        }
      }
      if (request.getParameter(AccessController.P_REGRADEWHOLE) != null) {         
        subProblems = null;
      } else if (subProblems.size() == 0) {
        result.addError("Please check the problems you would like to submit request for");
        return result;
      }
      
      if (!transactions.addRegradeRequest(p, group, subProblems, requestText)) {
        result.addError("Unexpected error; regrade could not be committed");
      }
    } catch (Exception e) {
      result.addError("Unexpected error; failed to submit regrade request");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * @return TransactionResult
   */
  public TransactionResult addStudentsToCourse(User pr, Course course,
      HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
      } else {
        Vector netids = new Vector();
        ExcelCSVParser p;
        boolean isFile = false;
        boolean isList = false;
        boolean emailOn = false;
        FileItem file = null;
        String list = null;
        DiskFileUpload upload = new DiskFileUpload();
        List info =
            upload.parseRequest(request, 1024, AccessController.maxFileSize);
        Iterator i = info.iterator();
        while (i.hasNext()) {
          FileItem item = (FileItem) i.next();
          String name = item.getFieldName();
          if (AccessController.P_ADDSTUDENTSFILE.equals(name)) {
            isFile = true;
          } else if (AccessController.P_ADDSTUDENTSLIST.equals(name)) {
            isList = true;
          } else if (AccessController.P_STUDENTSFILE.equals(name)) {
            file = item;
          } else if (AccessController.P_STUDENTSLIST.equals(name)) {
            list = item.getString();
          } else if (AccessController.P_EMAILADDED.equals(name)) {
            emailOn = true;
          } else {
            result.addError("Invalid form entries");
            return result;
          }
        }
        if (isList && isFile) {
          result.addError("Invalid form entries");
          return result;
        }
        if (isList) {
          netids.addAll(StringUtil.parseNetList(list));
        } else // isFile
        {
          InputStream stream = file.getInputStream();
          p = new ExcelCSVParser(stream);
          String[][] a = p.getAllValues();
          for (int j = 0; j != a.length; j++) {
            for (int k = 0; k != a[j].length; k++) {
              if (!"".equals(a[j][k])) {
                netids.add(a[j][k].trim().toLowerCase());
              }
            }
          }
        }
        result =
            transactions.addStudentsToCourse(pr, netids, course, emailOn);
      }
    } catch (FileUploadException e) {
      result.addError(FileUtil.checkFileException(e));
    } catch (Exception e) {
      e.printStackTrace();
      result.addError("Unexpected error; could not add students");
    }
    return result;
  }

  public TransactionResult assignGrader(User p, Assignment assign,
      SubProblem subProblem, User grader, Map requestMap) {
    TransactionResult result = new TransactionResult();
    try {
      Course course = assign.getCourse();
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      Collection groups = new LinkedList();
      Iterator i = requestMap.keySet().iterator();
      while (i.hasNext()) {
        String key = (String) i.next();
        if (key.startsWith(AccessController.P_GRADEGROUP)) {
          groups.add(database.getGroup(key.split(AccessController.P_GRADEGROUP)[1]));
        }
      }
      if (groups.size() > 0) {
        boolean success = transactions.assignGrader(p, assign, subProblem, grader, groups);
        if (!success) {
          result.addError("Database failed to update TA grading assignment");
        } else {
          result.setValue("Grader assignments successfully updated");
        }
      } else {
        result.setValue("No groups selected");
      }
    } catch (Exception e) {
      e.printStackTrace();
      result.addError("Database failed to update TA grading assignment");
    }
    return result;
  }

  /**
   * Returns true iff the given User has permission to download group
   * submissions from the given groups
   * 
   * @param p
   * @param groups
   * @return
   */
  public boolean authorizeGroupFiles(User p, Collection groups) {
    try {
      Assignment assign = database.isValidGroupCollection(groups);
      if (assign == null) return false;
      
      if (assign == null) return false;
      if (p.isAdminPrivByCourse(assign.getCourse())) {
        return true;
      } else if (p.isGradesPrivByCourse(assign.getCourse())) {
        if (assign.getAssignedGraders()) {
          return database.isAssignedTo(p.getNet(), groups);
        } else {
          return true;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

  /**
   * Cancels an invitation for a user to join a group
   * 
   * @param p
   *          The User of the user canceling this invitation (must be a
   *          member of the group in question)
   * @param canceled
   *          The Net of the user to uninvite
   * @param groupid
   *          The Group of the group
   * @return TransactionResult
   */
  public TransactionResult cancelInvitation(User p, User canceled, Group group) {
    TransactionResult result = new TransactionResult();
    try {
      Assignment assign = group == null ? null : group.getAssignment();
      if (group == null) {
        result.addError("Invalid group entered, does not exist");
        return result;
      }
      if (assign == null || assign.getHidden()) {
        result.addError("No corresponding assignment exists");
        return result;
      } else if (!assign.getStatus().equals(Assignment.OPEN)) {
        result
            .addError("Group management is not currently available for this assignment");
        return result;
      }
      if (courseIsFrozen(assign.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      GroupMember memCanceller = group.findGroupMember(p),
                  memCancelled = group.findGroupMember(canceled);
      if (memCanceller == null || !memCanceller.getStatus().equals(GroupMember.ACTIVE)) {
        result.addError("Must be an active group member to cancel an invitation");
      }
      if (memCancelled == null || !memCancelled.getStatus().equals(GroupMember.INVITED)) {
        if (memCancelled != null && memCancelled.getStatus().equals(GroupMember.ACTIVE)) {
          result.addError(canceled + " is already an active group member");
        } else {
          result.addError(canceled + " has not been invited to join this group");
        }
      }
      if (!result.hasErrors()) {
        if (transactions.cancelInvitation(p, canceled, groupid)) {
          result.setValue("Successfully canceled invitation");
        } else {
          result.addError("Database failed to cancel invitation");
        }
      }
    } catch (Exception e) {
      result.addError("Database failed to cancel invitation");
      e.printStackTrace();
    }
    return result;
  }

  // This is used whenever a student or staff member adds or removes a group
  // from a slot
  public TransactionResult changeGroupSlot(User p, Group group,
      Assignment assign, HttpServletRequest req, boolean canAssign,
      boolean canReplace) {
    TransactionResult result = new TransactionResult();
    TimeSlot slot = null;
    try {
      boolean addGroup = false;
      // look for slot id in servlet request
      if (canAssign) {
        String slotParam = req.getParameter(AccessController.P_TIMESLOTID);
        if (slotParam != null) {
          slot = database.getTimeSlot(slotParam);
          addGroup = true;
        }
      }
      // if not found, search postdata
      if ((!addGroup) && canAssign) {
        DiskFileUpload u = new DiskFileUpload();
        List info =
            u.parseRequest(req, 1024, AccessController.maxFileSize,
                FileUtil.TEMP_DIR);
        Iterator i = info.iterator();
        while (i.hasNext()) {
          FileItem item = (FileItem) i.next();
          String field = item.getFieldName();
          if (item.isFormField() && field.equals(AccessController.P_TIMESLOTID)) {
            slot = database.getTimeSlot(item.getString());
            addGroup = true;
          }
        }
      }
      // handle potential errors
      if (group == null) {
        result.addError("Invalid group entered: does not exist");
        return result;
      }
      if (addGroup && (slot == null || slot.getHidden())) {
        result.addError("Invalid time slot entered: does not exist or is hidden");
        return result;
      }
      if (assign == null || assign.getHidden() || (!assign.getScheduled())) {
        result.addError("Invalid assignment entered: does not exist or does not use schedule");
        return result;
      }
      if (courseIsFrozen(assign.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      // check for a full time slot
      if (addGroup) {
        int limit = 0;
        if (assign.getGroupLimit() != null)
          limit = assign.getGroupLimit().intValue();
        int population = slot.getPopulation();
        if (population >= limit) {
          result.addError("Requested time slot is full");
          return result;
        }
      }
      // check for an already assigned group
      if ((!canReplace) && addGroup) {
        TimeSlot currentSlot = group.getTimeSlot();
        if (currentSlot != null) {
          result.addError("Unauthorized reassignment of an already assigned group");
          return result;
        }
      }
      // check for locked schedule on this assignment (only important if change
      // is being requested by a student)
      if (!p.isStaffInCourseByCourse(assign.getCourse())
          && assign.getTimeslotLockTime() != null
          && new Date().after(assign.getTimeslotLockTime())) {
        result.addError("Assignment schedule is currently locked; students may not make changes");
        return result;
      }
      if (!result.hasErrors()) {

        TimeSlot slotNum = null;
        if (addGroup && slot != null) slotNum = slot;

        if (transactions.changeGroupSlot(p, groupid, assignid, slotNum, addGroup)) {
          if (addGroup)
            result.setValue("Successfully added group to time slot");
          else
            result.setValue("Successfully removed group from time slot");
        } else {
          result.addError("Database failed to make time slot change");
        }
      }
    } catch (Exception e) {
      result.addError("Database failed to make time slot change");
      e.printStackTrace();
    }
    return result;
  }

  // This is used whenever a student or staff member adds or removes a group
  // from a slot
  public TransactionResult changeGroupSlotBy(User p, Group group,
      Assignment assign, TimeSlot slot, boolean canAssign, boolean canReplace) {
    TransactionResult result = new TransactionResult();
    try {
      boolean addGroup = true;
      // handle potential errors
      if (group == null) {
        result.addError("Invalid group entered: does not exist");
        return result;
      }
      if (addGroup && (slot == null || slot.getHidden())) {
        result.addError("Invalid time slot entered: does not exist or is hidden");
        return result;
      }
      if (assign == null || assign.getHidden() || (!assign.getScheduled())) {
        result.addError("Invalid assignment entered: does not exist or does not use schedule");
        return result;
      }
      if (courseIsFrozen(assign.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      // check for a full time slot
      if (addGroup) {
        int limit = 0;
        if (assign.getGroupLimit() != null)
          limit = assign.getGroupLimit().intValue();
        int population = slot.getPopulation();
        if (population >= limit) {
          result.addError("Requested time slot is full");
          return result;
        }
      }
      // check for an already assigned group
      if ((!canReplace) && addGroup) {
        TimeSlot currentSlot = group.getTimeSlot();
        if (currentSlot != null) {
          result.addError("Unauthorized reassignment of an already assigned group");
          return result;
        }
      }
      // check for locked schedule on this assignment (only important if change
      // is being requested by a student)
      if (!p.isStaffInCourseByCourse(assign.getCourse())
          && assign.getTimeslotLockTime() != null
          && new Date().after(assign.getTimeslotLockTime())) {
        result.addError("Assignment schedule is currently locked; students may not make changes");
        return result;
      }
      if (!result.hasErrors()) {

        TimeSlot slotNum = null;
        if (addGroup) slotNum = slot;

        if (transactions.changeGroupSlot(p, group, assign, slotNum, addGroup)) {
          if (addGroup)
            result.setValue("Successfully added group to time slot");
          else
            result.setValue("Successfully removed group from time slot");
        } else {
          result.addError("Database failed to make time slot change");
        }
      }
    } catch (Exception e) {
      result.addError("Database failed to make time slot change");
      e.printStackTrace();
    }
    return result;
  }

  public TransactionResult commitFinalGradesFile(User p, Course course, List table) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes can be made to it");
      } else {
        result = transactions.commitFinalGradesFile(p, course, table);
      }
    } catch (Exception e) {
      result
          .addError("Unexpected error while trying to commit final grades file");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * @param assignment
   * @param grader
   * @param table
   * @return TransactionResult
   */
  public TransactionResult commitGradesFile(User p, Assignment assignment, List table) {
    TransactionResult result = new TransactionResult();
    boolean success = false;
    try {
      if (courseIsFrozen(assignment.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      Map subProbMap = database.getSubProblemMap(assignment);
      Map groups = database.getGroupMap(assignment);
      boolean checkCanGrade =
          !p.isAssignPrivByAssignment(assignment)
              && assignment.getAssignedGraders();
      if (checkCanGrade) {
        String[] header = (String[]) table.get(0);
        long[] subProbs = new long[header.length - 1];
        String grader = p.getNet();

        int[] colsFound = CSVFileFormatsUtil.parseColumnNamesFlexibly(header);
        int netIndex =
            CSVFileFormatsUtil.getFlexibleColumnNum(colsFound,
                CSVFileFormatsUtil.NET);

        for (int i = 0; i < header.length; i++) {
          if (i != netIndex) {
            String[] data = (String[]) table.get(i);
            subProbs[i - 1] =
                ((Long) subProbMap.get(header[i])).longValue();
          }
        }

        HashSet canGrades = new HashSet();
        Iterator assignTos =
            database.groupAssignedToHome().findByNetAssignment(
                p.getNet(), assignment).iterator();
        while (assignTos.hasNext()) {
          GroupAssignedTo a = (GroupAssignedTo) assignTos.next();
          canGrades.add(a.getGroup() + "_" + a.getSubProblem());
        }
        for (int i = 1; i < table.size(); i++) {
          String[] data = (String[]) table.get(i);
          String netid = (String) data[netIndex];
          Group group = ((Long) groups.get(netid)).longValue();
          for (int j = 0; j < data.length; j++) {
            if (j != netIndex) {
              try {
                float grade = StringUtil.parseFloat(data[j]);
                long subProb = subProbs[j - 1];
                if (!canGrades.contains(group + "_" + subProb)) {
                  result.addError("No permission to grade " + netid
                      + " in column '" + (j + 1) + "'");
                }
              } catch (NumberFormatException x) {
                if (!data[j].equals("")) // empty string is ok
                  result.addError("Badly formatted grade for " + netid
                      + " in column '" + (j + 1) + "'");
              }
            }
          }
        }
      }
      if (!result.hasErrors()) {
        success = transactions.commitGradesFile(p, assignment, table);
      }
    } catch (Exception e) {
      success = false;
      e.printStackTrace();
    }
    if (!success) {
      result.addError("Database did not accept uploaded grades file");
    }
    return result;
  }

  /**
   * Commit whatever info was parsed from a CSV input file
   * 
   * @param p
   * @param table
   *          A List of String[]s, one per student mentioned in the uploaded
   *          file; the first element holds the header strings, and all the data
   *          have been verified
   * @param course
   *          The  of the particular course involved if any, else null
   * @param isClasslist
   *          Whether we'll need to read but ignore a CU column (usually Net
   *          is used instead to identify records; the classlist format is a
   *          special case)
   * @return TransactionResult
   * @throws FinderException,
   *           RemoteException
   */
  public TransactionResult commitStudentInfo(User p, List table,
      Course course, boolean isClasslist) throws RemoteException {
    TransactionResult result = new TransactionResult();
    boolean success = false;
    if (courseIsFrozen(course)) {
      result.addError("Course is frozen; no changes may be made to it");
      return result;
    }
    try {
      success = transactions.commitStudentInfo(p, table, course, isClasslist);
    } catch (Exception e) {
      success = false;
      e.printStackTrace();
    }
    if (!success)
      result.addError("Database did not accept uploaded file");
    else result.setValue("Student information successfully committed");
    return result;
  }

  /**
   * To be called from every function in TransactionHandler that changes a
   * course, before trying to make changes
   * 
   * @return Whether the course being dealt with is available for changes
   */
  private boolean courseIsFrozen(Course course) {
    // TODO: check if course is null
    return course.getFreezeCourse();
  }

  /**
   * Staff-level method for putting a group of students into a group together in
   * the given assignment. Requires that each student in the collection of
   * Nets is currently in a group by him/herself.
   * 
   * @param p
   * @param netids
   *          A List of Strings in correct Net format
   * @param assignment
   * @return
   */
  public TransactionResult createGroup(User p, List netids,
      Assignment assignment) {
    TransactionResult result = new TransactionResult();
    try {
      if (netids.size() < 2) // list has correct format but no, or not enough,
                              // nets
      {
        result.addError("You must specify at least two Nets");
        return result;
      }
      Collection nonStudents =
          database.getNonStudentNets(netids, ((assignment == null) ? 0
              : assignment.getCourse()));
      Collection nonSoloMembers =
          database.getNonSoloGroupMembers(netids, assignment);
      Collection gradedMembers =
          database.getGradedStudents(netids, assignment);
      if (assignment == null) {
        result.addError("Assignment does not exist in the database");
        return result;
      }
      if (courseIsFrozen(assignment.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      if (nonStudents.size() > 0) {
        String error = Util.listUniqueElements(nonStudents);
        error +=
            (nonStudents.size() > 1 ? " are not students in this course or have been dropped"
                : " is not a student in this course or has been dropped");
        result.addError(error);
      }
      if (nonSoloMembers.size() > 0) {
        String error = Util.listUniqueElements(nonSoloMembers);
        error +=
            (nonSoloMembers.size() > 1 ? " are already in groups for this assignment"
                : " is already in a group for this assignment");
        result.addError(error);
      }
      if (gradedMembers.size() > 0) {
        String error = Util.listUniqueElements(gradedMembers);
        error +=
            (gradedMembers.size() > 1 ? " have already received grades for this assignment. Their groups may not be altered."
                : " has already received a grade for this assignment. His/her group may not be altered.");
        result.addError(error);
      }
      if (!result.hasErrors()) {
        boolean success = transactions.createGroup(p, netids, assignment);
        if (!success) {
          result.addError("Database failed to group students");
        } else {
          result.setValue("Group was successfully created");
        }
      }
    } catch (Exception e) {
      result.addError("Database failed to group students");
    }
    return result;
  }

  /**
   * Merge all indicated students/groups into one group for the given assignment
   * 
   * @param p
   * @param asgn
   * @param groups
   *          A List of Longs holding the group s to consider
   * @return TransactionResult
   */
  public TransactionResult groupSelectedStudents(User p, Assignment assignment,
      List groups) {
    TransactionResult result = new TransactionResult();
    try {
      if (groups.isEmpty()) {
        result.setValue("No groups selected");
        return result;
      } else if (groups.size() == 1) {
        result.setValue("Only one group selected");
        return result;
      }
      if (assignment == null) {
        result.addError("Assignment does not exist in the database");
        return result;
      }
      if (courseIsFrozen(assignment.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      Collection gradedGroups = database.getGradedGroups(groups, assignment);
      if (gradedGroups.size() > 0) {
        String error = "Group", groupStr = "";
        Iterator i = gradedGroups.iterator();
        while (i.hasNext()) {
          Long groupid = (Long) i.next();
          Collection members =
              database.groupMemberHome().findActiveByGroup(
                  groupid.longValue());
          Collection netids = new ArrayList();
          Iterator i2 = members.iterator();
          while (i2.hasNext())
            netids.add(((GroupMember) i2.next()).getNet());
          groupStr += " (" + Util.listElements(netids) + ")";
        }
        error +=
            (gradedGroups.size() > 1 ? "s"
                + groupStr
                + " have already received grades for this assignment and may not be altered"
                : groupStr
                    + " has already received a grade for this assignment and may not be altered");
        result.addError(error);
      }
      if (!result.hasErrors()) {
        boolean success = transactions.mergeGroups(p, groups, asgn);
        if (!success) {
          result.addError("Database failed to merge groups");
        } else {
          result.setValue("Groups were successfully merged");
        }
      }
    } catch (Exception e) {
      result.addError("Database failed to merge groups");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Break up all indicated groups and put their members into individual groups
   * for the given assignment; ignore all selected single students (this last is
   * done in Xactions)
   * 
   * @param p
   * @param asgn
   * @param groups
   *          A List of Longs holding the group s to consider
   * @return TransactionResult
   */
  public TransactionResult ungroupSelectedStudents(User p, Assignment assignment, List groups) {
    TransactionResult result = new TransactionResult();
    try {
      if (groups.isEmpty()) {
        result.setValue("No students selected");
        return result;
      }
      if (assignment == null) {
        result.addError("Assignment does not exist in the database");
        return result;
      }
      if (courseIsFrozen(assignment.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      Collection gradedGroups = database.getGradedGroups(groups, assignment);
      if (gradedGroups.size() > 0) {
        String error = "Group", groupStr = "";
        Iterator i = gradedGroups.iterator();
        while (i.hasNext()) {
          Long groupid = (Long) i.next();
          Collection members =
              database.groupMemberHome().findActiveByGroup(
                  groupid.longValue());
          Collection netids = new ArrayList();
          Iterator i2 = members.iterator();
          while (i2.hasNext())
            netids.add(((GroupMember) i2.next()).getNet());
          groupStr += " (" + Util.listElements(netids) + ")";
        }
        error +=
            (gradedGroups.size() > 1 ? "s"
                + groupStr
                + " have already received grades for this assignment and may not be altered"
                : groupStr
                    + " has already received a grade for this assignment and may not be altered");
        result.addError(error);
      }
      if (!result.hasErrors()) {
        boolean success = transactions.disbandGroups(p, groups, asgn);
        if (!success) {
          result.addError("Database failed to disband group(s)");
        } else {
          result.setValue("Groups were successfully disbanded");
        }
      }
    } catch (Exception e) {
      result.addError("Database failed to disband group(s)");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Add a new semester by name
   * 
   * @param semesterName
   *          The selected name string (will be checked for legality)
   * @return TransactionResult
   */
  public TransactionResult createSemester(User p, String semesterName) {
    TransactionResult result = new TransactionResult();
    boolean success = true;
    if (!Util.isLegalSemesterName(semesterName)) {
      result.addError("'" + semesterName + "' is not a legal semester name");
      return result;
    }
    try {
      success = transactions.createSemester(p, semesterName);
    } catch (Exception e) {
      success = false;
      result.addError("Database failed to create semester");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Declines an invitation to a user to join a group.
   * 
   * @param netid
   *          The Net of the user whose invitation is being declined
   * @param groupid
   *          The Group of the group
   * @return TransactionResult
   */
  public TransactionResult declineInvitation(User p, Group group) {
    TransactionResult result = new TransactionResult();
    try {
      String netid = p.getNet();
      Assignment assign = group.getAssignment();
      if (group == null) {
        result.addError("Invalid group entered: does not exist");
        return result;
      }
      if (assign == null || assign.getHidden()) {
        result.addError("No corresponding assignment exists");
        return result;
      } else if (!assign.getStatus().equals(Assignment.OPEN)) {
        result
            .addError("Group management is not currently available for this assignment");
        return result;
      }
      if (courseIsFrozen(assign.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      GroupMember member = null;
      try {
        member =
            database.groupMemberHome().findByPrimaryKey(
                new GroupMemberPK(groupid, netid));
      } catch (Exception e) {
      }
      if (member == null || !member.getStatus().equals(GroupMember.INVITED)) {
        if (member != null && member.getStatus().equals(GroupMember.ACTIVE)) {
          result.addError("Already an active member of this group");
        } else {
          result.addError("No invitation to join this group exists");
        }
      }
      if (transactions.declineInvitation(p, groupid)) {
        result.setValue("Successfully declined invitation");
      } else {
        result.addError("Database failed to update declined invitation");
      }
    } catch (Exception e) {
      result.addError("Database failed to update declined invitation");
      e.printStackTrace();
    }
    return result;
  }

  public TransactionResult disbandGroup(User p, Group group) {
    TransactionResult result = new TransactionResult();
    try {
      if (group == null) {
        result.addError("Group does not exist in database.");
        // FIXME check for group members already graded
      } else {
        Assignment assignment = group.getAssignment();
        if (courseIsFrozen(assignment.getCourse())) {
          result.addError("Course is frozen; no changes may be made to it");
          return result;
        }
        boolean success = transactions.disbandGroup(p, group);
        if (!success)
          result.addError("Database failed to ungroup selected groups");
      }
    } catch (Exception e) {
      result.addError("Database failed to ungroup selected groups");
    }
    return result;
  }

  public TransactionResult dropStudent(User p, Course course,
      Collection nets) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
      } else {
        Collection nonExist = database.getNonStudentNets(nets, course);
        if (nonExist.size() > 0) {
          result.addError(Util.listElements(nonExist)
              + (nonExist.size() == 1 ? " is not an enrolled student"
                  : " are not enrolled students") + " in the course");
        } else {
          if (transactions.dropStudents(p, nets, course)) {
            result.setValue("Student" + (nets.size() > 1 ? "s" : "")
                + " successfully dropped");
          } else {
            result.addError("Unexpected error while trying to drop student");
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      result.addError("Unexpected error while trying to drop student");
    }
    return result;
  }

  /**
   * Edits an already existing announcement
   * 
   * @param announce
   *           of the annoucement to edit
   * @param announce
   *          Text of the revised announcement
   * @param poster
   *          Person who posted the original announcement
   * @return TransactionResult
   */
  public TransactionResult editAnnouncement(User p, Announcement annt,
      String newText, boolean remove) {
    TransactionResult result = new TransactionResult();
    if (annt == null)
      result.addError("Couldn't find announcement in database");
    else if (courseIsFrozen(annt.getCourse()))
      result.addError("Course is frozen; no changes may be made to it");
    else try {
      transactions.editAnnouncement(p, annt, newText, remove);
    } catch (Exception e) {
      result.addError("Could not edit announcement due to database error");
    }
    return result;
  }

  /**
   * Set a semester's properties. This function doesn't use a custom data object
   * because hopefully hiddenness will always be the only settable semester
   * property.
   * 
   * @param semester
   * @param hidden
   * @return TransactionResult
   */
  public TransactionResult editSemester(User p, Semester semester, boolean hidden) {
    TransactionResult result = new TransactionResult();
    boolean success = true;
    try {
      transactions.editSemester(p, semester, hidden);
    } catch (Exception e) {
      result.addError("Database failed to edit semester", e);
    }
    return result;
  }

  public TransactionResult exportSurveyResult(OutputStream out,
      Collection surveyResultData) {
    TransactionResult result = new TransactionResult();
    ExcelCSVPrinter printer = new ExcelCSVPrinter(out);
    Iterator i = surveyResultData.iterator();
    while (i.hasNext()) {
      String[] line = (String[]) i.next();
      try {
        printer.writeln(line);
      } catch (IOException ex) {
        result.addError("Error writing a row");
        return result;
      }
    }
    return result;
  }

  /**
   * Write a full template for student data, including all columns the
   * flexible-upload feature can accept. The user should remove the columns that
   * aren't wanted. If there isn't a course , only write cmsadmin columns; if
   * there is one, write all columns except CU, which only cmsadmin should
   * touch.
   * 
   * @param course
   *          A Long giving the course  for a course-specific download, or
   *          null for a cmsadmin download
   * @param out
   *          The stream to which to write the resulting CSV file
   * @return TransactionResult
   */
  public TransactionResult exportStudentInfoTemplate(Course course, OutputStream out) {
    TransactionResult result = new TransactionResult();
    CSVPrinter printer = new CSVPrinter(out);
    List headers = new ArrayList(); // will hold objects of type
                                    // CSVFileFormatsUtil.ColumnInfo
    if (course == null) // admin info only
    {
      for (int i = 0; i < CSVFileFormatsUtil.ALLOWED_COLUMNS.length; i++)
        if (CSVFileFormatsUtil.ALLOWED_COLUMNS[i].isForAdmin())
          headers.add(CSVFileFormatsUtil.ALLOWED_COLUMNS[i]);
    } else // course info only
    {
      for (int i = 0; i < CSVFileFormatsUtil.ALLOWED_COLUMNS.length; i++)
        if (CSVFileFormatsUtil.ALLOWED_COLUMNS[i].isForStaff())
          headers.add(CSVFileFormatsUtil.ALLOWED_COLUMNS[i]);
    }
    String[] headerLine = new String[headers.size()];
    for (int i = 0; i < headerLine.length; i++)
      headerLine[i] =
          ((CSVFileFormatsUtil.ColumnInfo) headers.get(i)).getName();
    try {
      printer.writeln(headerLine);
    } catch (IOException x) {
      result.addError("Couldn't print to template file");
      return result;
    }
    // write one line of commas with empty values between
    String[] commaLine = new String[headerLine.length];
    // CSVPrinter will wrap the first (empty) token with quotes; can't think of
    // a way to avoid that--Evan
    for (int i = 0; i < commaLine.length; i++)
      commaLine[i] = "";
    try {
      printer.writeln(commaLine);
    } catch (IOException x) {
      result.addError("Couldn't print to template file");
      return result;
    }
    return result;
  }

  /**
   * @return The InitalContext
   * @throws NamingException
   *           If naming exception is encountered
   */
  private InitialContext getContext() throws NamingException {
    Hashtable props = new Hashtable();
    props.put(InitialContext.INITIAL_CONTEXT_FACTORY,
        "org.jnp.interfaces.NamingContextFactory");
    props.put(InitialContext.PROVIDER_URL, "jnp://localhost:1099");
    InitialContext ic = new InitialContext(props);
    return ic;
  }

  /**
   * Parse a servlet request and return a collection of FileItems which
   * represent the files being uploaded by the user.
   * 
   * @param request
   *          The servlet request with uploaded files
   * @return Returns a Collection of FileItems
   */
  private Collection getUploadedFiles(User user, HttpServletRequest request,
      boolean isLate) throws FileUploadException {
    Profiler.enterMethod("TransactionHandler.getUploadedFiles", "");
    DiskFileUpload upload = new DiskFileUpload();
    Collection result = new ArrayList();
    java.io.File mkDir = new java.io.File(FileUtil.TEMP_DIR);
    if (!mkDir.exists()) mkDir.mkdirs();
    try {
      Assignment assignment = database.getAssignment(request.getParameter(AccessController.P_ASSIGNID));
      Group group = assignment.findGroup(user);
      List files =
          upload.parseRequest(request, 1024, AccessController.maxFileSize,
              FileUtil.TEMP_DIR);
      Iterator i = files.iterator();
      while (i.hasNext()) {
        FileItem file = (FileItem) i.next();
        long submission =
            database.getRequiredSubmission((file.getFieldName().split("file_"))[1]);
        if (!file.getName().equals("")) {
          String fullFileName = FileUtil.trimFilePath(file.getName()), givenFileName =
              null, givenFileType = null;
          String[] split = FileUtil.splitFileNameType(fullFileName);
          givenFileName = split[0];
          givenFileType = split[1];
          RequiredSubmission submission =
              database.requiredSubmissionHome().findByPrimaryKey(
                  new RequiredSubmissionPK(submission));
          RequiredFileTypeData match = submission.matchFileType(givenFileType);
          if (match == null)
            throw new FileUploadException("match fail:" + fullFileName);
          if (file.getSize() / 1024 > submission.getMaxSize())
            throw new FileUploadException("size violation:" + fullFileName);
          int fileCount = transactions.getGroupFileCounter(group.getGroup());
          java.io.File movedFile, path;
          movedFile =
              new java.io.File(FileUtil.getSubmittedFileSystemPath(assignment
                  .getCourse(), submission.getAssignment(), group
                  .getGroup(), fileCount, match.getSubmission(), match
                  .getFileType()));
          path = movedFile.getParentFile();
          if (path.exists())
            throw new FileUploadException(
                "Failed to create a unique file path for uploaded file.");
          if (!path.mkdirs())
            throw new FileUploadException(
                "Failed to create new directory on the file system");
          if (!movedFile.createNewFile()) {
            throw new FileUploadException(
                "System failed to accept submitted file");
          }
          String md5 = FileUtil.calcMD5(file);
          file.write(movedFile);
          result.add(new SubmissionInfo(submission.getRequiredSubmissionData(),
              match.getFileType(), fileCount, md5, (int) file.getSize(),
              fullFileName, isLate));
        }
      }
    } catch (FileUploadException e) {
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
      throw new FileUploadException("Error getting uploaded files");
    }
    Profiler.exitMethod("TransactionHandler.getUploadedFiles", "");
    return result;
  }

  /**
   * Create an invitation for a user to join a group.
   * 
   * @param p
   *          The User of the user inviting another user to join their
   *          group
   * @param invited
   *          The Net of the user being invited to join
   * @param groupid
   *          The Group of the group
   * @return An error string that's empty ("", NOT null) if no error
   */
  public TransactionResult inviteUser(User inviter, User invited, Group group) {
    TransactionResult result = new TransactionResult();
    try {
      Assignment assign = group.getAssignment();
      if (group == null) {
        result.addError("Group does not exist in the database");
        return result;
      }
      if (assign == null) {
        result.addError("No assignment corresponding to the given group");
        return result;
      }
      if (assign.getHidden()) {
        result.addError("No assignment corresponding to the given group");
        return result;
      }
      if (!assign.getStatus().equals(Assignment.OPEN)) {
        result.addError("Group management is not currently available for this assignment");
        return result;
      }
      if (courseIsFrozen(assign.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      if (assign.getAssignedGroups()) {
        result.addError("Students are not allowed to create their own groups for this assignment");
      }
      int numMembers = group.findActiveMembers().size();
      if (numMembers >= assign.getGroupSizeMax()) {
        result.addError("Cannot invite students; group is already full");
      }
      GroupMember memInviter = group.findGroupMember(inviter),
                  memInvited = group.findGroupMember(invited);
      Student studentInviter = memInviter.getStudent(),
              studentInvited = memInvited.getStudent();
      if (memInviter == null || !memInviter.getStatus().equals(GroupMember.ACTIVE)) {
        result.addError("Must be an active member in a group to invite");
      }
      if (studentInviter == null || !studentInviter.getStatus().equals(Student.ENROLLED)) {
        result.addError("Must be an enrolled student in this course to create group invitations");
      }
      if (studentInvited == null || !studentInvited.getStatus().equals(Student.ENROLLED)) {
        result.addError("Invited Net is not an enrolled student in this course");
      }
      if (!result.hasErrors()) {
        if (memInvited != null) {
          if (memInvited.getStatus().equalsIgnoreCase(GroupMember.INVITED)) {
            result.addError("This student has already been invited to join this group");
          } else if (memInvited.getStatus().equalsIgnoreCase(GroupMember.ACTIVE)) {
            result.addError("This student is already a member of this group");
          } else {
            if (transactions.inviteUser(p, invited, groupid)) {
              result.setValue("Invited " + invited.getNetID() + " to join the group");
            } else {
              result.addError("Database failed to invite student");
            }
          }
        } else {
          if (transactions.inviteUser(p, invited, groupid)) {
            result.setValue("Invited " + invited.getNetID() + " to join the group");
          } else {
            result.addError("Database failed to invite student");
          }
        }
      }
    } catch (Exception e) {
      result.addError("Database failed to invite student");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Remove a user from a group. Has the side effect of creating a new, empty
   * group in the same assignment and adding the user to it.
   * 
   * @param netid
   *          The Net of the user to remove
   * @param groupid
   *          The Group of the group
   * @return An error string that's empty ("", NOT null) if no error
   */
  public TransactionResult leaveGroup(User p, Group group) {
    TransactionResult result = new TransactionResult();
    try {
      String netid = p.getNet();
      Assignment assign = group == null ? null : group.getAssignment();
      if (group == null) {
        result.addError("Invalid group entered: does not exist");
        return result;
      }
      if (assign == null || assign.getHidden()) {
        result.addError("No corresponding assignment exists");
        return result;
      } else if (!assign.getStatus().equals(Assignment.OPEN)) {
        result
            .addError("Group management is not currently available for this assignment");
        return result;
      }
      if (courseIsFrozen(assign.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      GroupMember member = null;
      try {
        member =
            database.groupMemberHome().findByPrimaryKey(
                new GroupMemberPK(groupid, netid));
      } catch (Exception e) {
      }
      if (member == null || !member.getStatus().equals(GroupMember.ACTIVE)) {
        result.addError("Not an active member of this group");
      }
      Collection grades =
          database.gradeHome().findMostRecentByNetAssignment(netid,
              assign.getAssignment());
      if (grades != null && grades.size() > 0) {
        result
            .addError("Cannot leave this group because grades have been entered");
      }
      int numMembers =
          database.groupMemberHome().findActiveByGroup(groupid).size();
      if (numMembers < 2) {
        result.addError("Cannot leave a solo group");
      }
      if (!result.hasErrors()) {
        if (transactions.leaveGroup(p, groupid)) {
          result.setValue("Successfully left group");
        } else {
          result.addError("Database failed to remove group member");
        }
      }
    } catch (Exception e) {
      result.addError("Database failed to remove group member");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Search through request looking for a file with the given parameter name; if
   * it isn't found, return null
   * 
   * @param request
   * @param paramName
   * @param result
   *          The object to which to append an error if the file isn't found
   * @return FileItem
   */
  private FileItem retrieveUploadedFile(HttpServletRequest request,
      String paramName, TransactionResult result) {
    DiskFileUpload upload = new DiskFileUpload();
    FileItem file = null;
    try {
      Iterator items =
          upload.parseRequest(request, 1024, AccessController.maxFileSize,
              FileUtil.TEMP_DIR).iterator();
      while (items.hasNext()) {
        FileItem item = (FileItem) items.next();
        if (item.getFieldName().equals(paramName)) {
          file = item;
          break;
        }
      }
    } catch (FileUploadException x) {
      result
          .addError("Unexpected error receiving uploaded file; please try again");
    }
    if (file == null || file.getName().equals(""))
      result.addError("Error receiving uploaded file; please try again");
    return file;
  }

  /**
   * Read the first line of a CSV file and make sure it fits the given file
   * format
   * 
   * @param parser
   * @param format
   * @param result
   *          The object to which to append errors if bad things happen
   * @return A String[] containing the values on the header line, or an
   *         undefined value if result contains errors
   */
  private String[] readCSVHeaderLine(CSVParse parser, String[] format,
      TransactionResult result) {
    String[] line = null;
    int expectedLineLength = CSVFileFormatsUtil.getNumColumns(format);
    try {
      line = parser.getLine();
      if (line == null) {
        result.addError("Uploaded file is empty");
      }
    } catch (IOException x) {
      result.addError("Error parsing header line of uploaded file");
    }
    // make sure the header line looks vaguely right
    if (line.length != expectedLineLength) {
      result.addError("Header line of uploaded file doesn't have "
          + expectedLineLength + " columns.\n" + "Columns should be "
          + CSVFileFormatsUtil.getColumnListing(format) + ".");
    }
    for (int i = 0; i < expectedLineLength; i++) {
      line[i] = line[i].trim();
      if (!line[i].equalsIgnoreCase(format[i]))
        result.addError("Column " + (i + 1)
            + " of header line should resemble '" + format[i] + "'");
    }
    return line;
  }

  /**
   * Flexibly parse the included CSV file and return the data found in it, which
   * should provide at least one predefined kind of information for a group of
   * students. There must be a Net column unless we're adhering to a specific
   * file format (the second argument). Check to make sure that if a Net and
   * CU are provided for a student and they're already in the database, the
   * values in the file check with those in the db. Check that all students
   * listed in the file exist in the database. If this is an upload for a
   * course, check that all students listed in the file are enrolled in the
   * course. (In the case of a class-specific upload with a required format
   * without Net, this means checking that all included CUs belong to
   * students enrolled in the class.) Check that no columns not
   * permission-appropriate for the upload type (course, system) are included
   * (just ignore them if they are). Don't require that all students in the
   * course be listed in an upload; we want to allow adding info for just a few
   * students as it becomes available.
   * 
   * @param request
   * @param requiredFormat
   *          If not null, the format (CSVFileFormatsUtil.<SOMETHING>_FORMAT)
   *          the file header must match
   * @return A TransactionResult. If the upload is successful, the TR's data
   *         object will be a List of String[]s with one entry for each student
   *         for whom data was read; the elements in each array will be the
   *         columns read in (null in each case if no data). The first list
   *         entry will contain the data in the header row of the file.
   */
  public TransactionResult parseCSVInfo(HttpServletRequest request,
      String[] requiredFormat) {
    TransactionResult result = new TransactionResult();
    List values = new ArrayList();
    try {
      Course course = database.getCourse(request.getParameter(AccessController.P_COURSEID));
      // if course == null, this is a staff upload
      // otherwise it is a cmsadmin upload
      FileItem file =
          retrieveUploadedFile(request, AccessController.P_UPLOADEDCSV, result);
      if (result.hasErrors()) return result;
      // read the file
      BufferedReader listlines =
          new BufferedReader(new InputStreamReader(file.getInputStream()));
      ExcelCSVParser csvParser = new ExcelCSVParser(listlines);
      // try to recognize header line
      String[] line = csvParser.getLine();
      int[] infoFound = CSVFileFormatsUtil.parseColumnNamesFlexibly(line);
      values.add(line);
      for (int i = 0; i < infoFound.length; i++)
        if (infoFound[i] == -1) // the ith column name wasn't recognized
          result.addError("Unknown column header '" + line[i] + "'");
      if (result.hasErrors()) return result;
      // if a required format is specified, make sure it matches
      if (requiredFormat != null
          && !CSVFileFormatsUtil.headerLineMatchesFormat(infoFound,
              requiredFormat)) {
        result.addError("Header line does not match required format");
        return result;
      }
      // if no format is specified, require that Net appear
      int netidCol =
          CSVFileFormatsUtil.getFlexibleColumnNum(infoFound,
              CSVFileFormatsUtil.NET);
      if (requiredFormat == null && netidCol == -1) {
        result.addError("Net column must appear in uploaded file");
        return result;
      }
      // parse all further lines using assumed format
      int lineNum = 2, expectedLineLength = line.length;
      try {
        line = csvParser.getLine();
      } catch (IOException x) {
        result.addError("Error parsing CSV on line " + lineNum);
        return result;
      }
      while (line != null) { // null return value means end of file
        try {
          if (line.length != expectedLineLength) {
            result.addError("Line " + lineNum + " doesn't have "
                + expectedLineLength + " columns");
            return result;
          }
          // if no format requirement, always require Net
          if (requiredFormat == null) {
            if (!line[netidCol].matches(CSVFileFormatsUtil.netidRegexp)) {
              result.addError("Net on line " + lineNum + " does not parse");
            }
            if (result.hasErrors()) return result;
            // require *existing* Net
            try {
              User user =
                  database.userHome().findByPrimaryKey(
                      new UserPK(line[netidCol]));
            } catch (FinderException x) {
              result.addError("User '" + line[netidCol] + "' does not exist",
                  lineNum);
            }
            if (result.hasErrors()) return result;
          }
          // check data formatting
          String[] checkedLine = new String[expectedLineLength];
          for (int i = 0; i < expectedLineLength; i++) {
            line[i] = line[i].trim();
            if (infoFound[i] != -1) {
              CSVFileFormatsUtil.ColumnInfo colInfo =
                  CSVFileFormatsUtil.ALLOWED_COLUMNS[infoFound[i]];
              if (colInfo.getDatatype() == CSVFileFormatsUtil.ColumnInfo.TYPE_STRING) {
                if (colInfo.getRegexp() != null
                    && !line[i].matches(colInfo.getRegexp())) {
                  result.addError("Couldn't parse formatted string in column "
                      + (i + 1) + " on line " + lineNum);
                } else checkedLine[i] = line[i];
              } else if (colInfo.getDatatype() == CSVFileFormatsUtil.ColumnInfo.TYPE_INT) {
                try {
                  Integer datum = Integer.valueOf(line[i]);
                  checkedLine[i] = datum.toString();
                } catch (NumberFormatException x) {
                  result.addError("Couldn't parse integer in column " + (i + 1)
                      + " on line " + lineNum);
                }
              } else if (colInfo.getDatatype() == CSVFileFormatsUtil.ColumnInfo.TYPE_FLOAT) {
                try {
                  Float datum = Float.valueOf(line[i]);
                  checkedLine[i] = datum.toString();
                } catch (NumberFormatException x) {
                  result.addError("Couldn't parse real in column " + (i + 1)
                      + " on line " + lineNum);
                }
              }
            }
          }
          if (result.hasErrors()) return result;
          values.add(checkedLine);
          line = csvParser.getLine();
        } catch (IOException x) {
          result.addError("Error parsing line " + lineNum);
          return result;
        }
        lineNum++;
      }
      result.setValue(values);
      /* data integrity & security checks */
      int cuidCol =
          CSVFileFormatsUtil.getFlexibleColumnNum(infoFound,
              CSVFileFormatsUtil.CU);
      if (netidCol != -1) {
        // if this is a course-specific upload, make sure all users included are
        // students in the course
        if (course != 0)
          for (int i = 1; i < values.size(); i++) {
            String netid = ((String[]) values.get(i))[netidCol];
            try {
              Student student =
                  database.studentHome().findByPrimaryKey(
                      new StudentPK(course, netid));
            } catch (FinderException x) {
              result.addError("User '" + netid
                  + "' is not a student in this course");
            }
          }
        if (result.hasErrors()) return result;
        // CU-related checks
        if (cuidCol != -1) {
          HashMap netid2cuid = new HashMap(); // for all students in course
          Collection courseStudents =
              database.userHome().findByCourse(course);
          Iterator it = courseStudents.iterator();
          while (it.hasNext()) {
            User student = (User) it.next();
            netid2cuid.put(student.getNet(), student.getCU());
          }
          // if this is a course-specific upload and cuid is included, all
          // students should have cuids in the db
          if (course != 0) {
            for (int i = 1; i < values.size(); i++) {
              line = (String[]) values.get(i);
              if (!netid2cuid.containsKey(line[netidCol])
                  || netid2cuid.get(line[netidCol]) == null
                  || netid2cuid.get(line[netidCol]).equals(""))
                result.addError("Student on line " + (i + 1)
                    + " has no CU in CMS");
            }
          }
          if (result.hasErrors()) return result;
          // if netid & cuid both appear, make sure they match if we already
          // have both in the db
          for (int i = 1; i < values.size(); i++) { // run through data rows
            line = (String[]) values.get(i);
            String netid = line[netidCol], cuid = line[cuidCol];
            if (netid2cuid.get(netid) != null) {
              if (!cuid.equals("") && !netid2cuid.get(netid).equals(cuid))
                result.addError("Net and CU on line " + (i + 1)
                    + " are not a match");
            }
          }
        }
        if (result.hasErrors()) return result;
      } else { // no netid (there's a required format and it doesn't contain
                // netid)
        // CU-related checks
        if (course != 0) { // class-specific upload
          // make sure each CU corresponds to a student in the class
          HashSet cuids = new HashSet(); // for all students in course
          Collection courseStudents =
              database.userHome().findByCourse(course);
          Iterator it = courseStudents.iterator();
          while (it.hasNext()) {
            User student = (User) it.next();
            cuids.add(student.getCU());
          }
          for (int i = 1; i < values.size(); i++) { // run through data rows
            line = (String[]) values.get(i);
            if (!cuids.contains(line[cuidCol]))
              result.addError("CU on line " + (i + 1)
                  + " does not belong to a student in this class");
          }
          if (result.hasErrors()) return result;
        }
      }
      /*
       * silently remove data from the columns that aren't appropriate for the
       * principal's permission level
       */
      boolean[] colsOK = new boolean[infoFound.length];
      int numColsOK = 0;
      for (int i = 0; i < infoFound.length; i++) {
        if (course == 0) // cmsadmin upload
          colsOK[i] =
              CSVFileFormatsUtil.ALLOWED_COLUMNS[infoFound[i]].isForAdmin();
        else colsOK[i] =
            CSVFileFormatsUtil.ALLOWED_COLUMNS[infoFound[i]].isForStaff();
        numColsOK += (colsOK[i] ? 1 : 0);
      }
      // special case: for a course-specific upload, leave in the CU (we'll
      // need to remove it later)
      if (course != 0 && cuidCol != -1 && !colsOK[cuidCol]) {
        numColsOK++;
        colsOK[cuidCol] = true;
      }
      for (int i = 1; i < values.size(); i++) {
        line = new String[numColsOK];
        int k = 0;
        for (int j = 0; j < infoFound.length; j++)
          if (colsOK[j]) line[k++] = ((String[]) values.get(i))[j];
        values.set(i, line);
      }
      // remove unwanted column headers
      line = (String[]) values.get(0);
      String[] newLine = new String[numColsOK];
      int k = 0;
      for (int j = 0; j < infoFound.length; j++)
        if (colsOK[j]) newLine[k++] = line[j];
      values.set(0, newLine);
    } catch (Exception x) {
      x.printStackTrace();
      result.addError("Unexpected error while trying to parse uploaded file");
    }
    return result;
  }

  /**
   * @param course
   * @param request
   * @return A TransactionResult. If the upload is successful, the TR's data
   *         object will be a List of String[]s with one entry for each student
   *         for whom data was read; the elements in each array will be the
   *         columns read in (null in each case if no data). The first list
   *         entry will contain the data in the header row of the file.
   */
  public TransactionResult parseFinalGradesFile(Course course, HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    Collection lines = new ArrayList();
    boolean hasSubProbs = false;
    HashSet netids = new HashSet();
    final String[] format = CSVFileFormatsUtil.FINALGRADES_TEMPLATE_FORMAT;
    List values = new ArrayList();
    try {
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      Iterator students =
          database.studentHome().findByCourseSortByLastName(course)
              .iterator();
      while (students.hasNext()) {
        Student s = (Student) students.next();
        netids.add(s.getNet());
      }
      FileItem file =
          retrieveUploadedFile(request, AccessController.P_GRADESFILE, result);
      if (result.hasErrors()) return result;
      // read the file
      BufferedReader listlines =
          new BufferedReader(new InputStreamReader(file.getInputStream()));
      ExcelCSVParser csvParser = new ExcelCSVParser(listlines);
      int netidCol =
          CSVFileFormatsUtil.getColumnNumber(format, CSVFileFormatsUtil.NET);
      int gradeCol =
          CSVFileFormatsUtil.getColumnNumber(format,
              CSVFileFormatsUtil.FINAL_GRADE);
      String[] line = readCSVHeaderLine(csvParser, format, result);
      if (result.hasErrors()) return result;
      String[] fileValues = new String[] { "Net", "Final Grade" };
      values.add(fileValues); // regardless of the file format, this is all the
                              // info we need
      int lineSize = fileValues.length;

      int lineNum = 2;
      try {
        line = csvParser.getLine();
      } catch (IOException x) {
        result.addError("Error parsing line " + lineNum + " of uploaded file");
        return result;
      }
      while (line != null) // null return value means end of file
      {
        try {
          // make sure that there is a grade entered
          String[] checkedLine = new String[lineSize];
          String net = "", grade = "";
          if (netidCol < line.length) {
            net = line[netidCol].toLowerCase().trim();
          }

          if (gradeCol < line.length) {
            grade = line[gradeCol].trim();
          }

          if (!netids.contains(net)) {
            result.addError(net
                + " is not an enrolled student in this course", lineNum);
          }
          checkedLine[0] = net;
          if (grade.equals("")) {
            checkedLine[1] = "";
          } else {
            String checkedGrade = Util.validGrade(grade);
            if (checkedGrade == null) {
              result.addError("'" + grade + "' is not a valid final grade",
                  lineNum);
              checkedLine[1] = grade;
            } else {
              checkedLine[1] = checkedGrade;
            }
          }
          values.add(checkedLine);
          line = csvParser.getLine();
        } catch (IOException x) {
          result
              .addError("Error parsing line " + lineNum + " of uploaded file");
          return result;
        }
        lineNum++;
      }
      result.setValue(values);
    } catch (Exception e) {
      e.printStackTrace();
      result
          .addError("An unexpected error occurred while trying to parse the final grades file");
    }
    return result;
  }

  /**
   * Return a TransactionResult holding a list with the grades that should be
   * entered into the system upon confirmation if no errors occur. If there is
   * no value uploaded, return a successful TransactionResult with a null value.
   * For any other error, the TransactionResult is not successful, the value is
   * null, and the result contains any errors that occurred.
   * 
   * @param request
   * @return A TransactionResult. If the upload is successful, the TR's data
   *         object will be a List of String[]s with one entry for each student
   *         for whom data was read; the elements in each array will be the
   *         columns read in (null in each case if no data). The first list
   *         entry will contain the data in the header row of the file.
   */
  public TransactionResult parseGradesFile(Assignment assignment, HttpServletRequest request) {
    Profiler.enterMethod("TransactionHandler.parseGradesFile", "Assignment: "
        + assignment.toString());
    TransactionResult result = new TransactionResult();
    boolean hasSubProbs = false;
    List values = new ArrayList();
    Iterator i = null;
    try {
      if (courseIsFrozen(assignment.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      Collection subProbs =
          database.subProblemHome().findByAssignment(assignment);
      Iterator students =
          database.studentHome().findByCourseSortByLastName(
              assignment.getCourse()).iterator();
      /*
       * Create these hash sets for easily telling later whether or not a value
       * we parsed is valid
       */
      Vector subProbNames = new Vector();
      HashSet netids = new HashSet();
      if (subProbs.size() == 0) {
        hasSubProbs = false;
      } else {
        hasSubProbs = true;
        i = subProbs.iterator();
        while (i.hasNext()) {
          SubProblem s = (SubProblem) i.next();
          subProbNames.add(s.getSubProblemName());
        }
      }
      while (students.hasNext()) {
        Student s = (Student) students.next();
        netids.add(s.getNet());
      }
      // Find the uploaded file stream
      FileItem file =
          retrieveUploadedFile(request, AccessController.P_GRADESFILE, result);
      if (result.hasErrors()) return result;
      // read the file
      BufferedReader listlines =
          new BufferedReader(new InputStreamReader(file.getInputStream()));
      ExcelCSVParser csvParser = new ExcelCSVParser(listlines);
      String[] line = csvParser.getLine();
      if (line == null) return result;
      int maxWidth = line.length;
      int[] colsFound = null, subproblemColsFound = null;
      try {
        colsFound = CSVFileFormatsUtil.parseColumnNamesFlexibly(line);
        subproblemColsFound =
            CSVFileFormatsUtil.parseSubProblemColumnNamesFlexibly(line,
                subProbNames);
      } catch (CSVParseException e) {
        result.addError(e.getMessage());
      }
      if (colsFound != null) {
        if (hasSubProbs) {
          // Make sure we have net at least
          if (CSVFileFormatsUtil.getFlexibleColumnNum(colsFound,
              CSVFileFormatsUtil.NET) == -1) {
            result.addError(
                "The file is misformatted: it must contain a net id", 0);
          }
        } else {
          // make sure the header line looks vaguely right
          final String[] format = CSVFileFormatsUtil.GRADES_FORMAT_NOSUBPROBS;

          for (int j = 0; j < format.length; j++) {
            if (CSVFileFormatsUtil.getFlexibleColumnNum(colsFound, format[j]) == -1) {
              result
                  .addError("Header line of uploaded file doesn't have the correct columns.\n"
                      + "Columns should include "
                      + CSVFileFormatsUtil.getColumnListing(format) + ".");
            }
          }
        }
      }
      values.add(line);
      int lineno = 2;

      int netIndex =
          CSVFileFormatsUtil.getFlexibleColumnNum(colsFound,
              CSVFileFormatsUtil.NET);
      int gradeIndex =
          CSVFileFormatsUtil.getFlexibleColumnNum(colsFound,
              CSVFileFormatsUtil.GRADE);
      if (colsFound == null) {
        while ((line = csvParser.getLine()) != null) {
          values.add(line);
        }
      } else {
        while ((line = csvParser.getLine()) != null) {
          String checkedLine[] = new String[line.length];

          for (int j = 0; j < line.length; j++)
            line[j] = line[j].trim();
          // Check that the Net that begins this line is a student in the
          // course
          if (!netids.contains(line[netIndex])) {
            result.addError("'" + line[netIndex]
                + "' is not an enrolled student in this course", lineno);
          }
          checkedLine[netIndex] = line[netIndex];

          /*
           * For each element after the first on a line, check that it properly
           * parses as a floating point number, and if it does, replace the
           * entry in checkedLine with a Float instead of a String
           */
          for (int j = 0; j < line.length; j++) {
            if (j != netIndex) {
              try {
                if (line[j].equals(""))
                  checkedLine[j] = "";
                else checkedLine[j] =
                    new Float(StringUtil.parseFloat(line[j])).toString();
              } catch (NumberFormatException e) {
                result.addError("'" + line[j] + "' is not a valid score",
                    lineno);
                checkedLine[j] = line[j];
              }
            }
          }
          values.add(checkedLine);
          lineno++;
        }
      }
      result.setValue(values);
    } catch (Exception e) {
      e.printStackTrace();
      result
          .addError("An unexpected exception occurred while trying to parse the file");
    }
    Profiler.exitMethod("TransactionHandler.parseGradesFile", "Assignment: "
        + assignment);
    return result;
  }

  /**
   * Posts a new announcement
   * 
   * @param course
   *          The course in which to post the announcement
   * @param announce
   *          Text of the announcement
   * @param poster
   *          User who posted this announcement
   * @return TransactionResult
   */
  public TransactionResult postAnnouncement(User p, Course course,
      String announce) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(course))
        result.addError("Course is frozen; no changes may be made to it");
      else {
        if (!transactions.postAnnouncement(p, course, announce))
          result.addError("Could not post announcement due to database error");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  public TransactionResult reenrollStudent(User p, Course course,
      User user, boolean emailOn) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
      } else {
        Student s = course.getStudent(user);
        if (s == null) {
          result.addError(user.getNetID()
              + " has not been previously enrolled in this class");
        } else {
          Vector n = new Vector();
          n.add(user);
          result = transactions.addStudentsToCourse(p, n, course, emailOn);
          if (!result.hasErrors()) {
            result.setValue(net + " has been successfully reenrolled");
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      result.addError("Unexpected error while trying to reenroll student");
    }
    return result;
  }

  public TransactionResult removeAssignment(User p, Assignment assignment) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(assignment.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
      } else {
        result = transactions.removeAssignment(p, assignment);
      }
    } catch (Exception e) {
      result.addError("Unexpected error while trying to remove assignment");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * @return 0 on error, the course  of the category on success
   */
  private Course removeCategory(User p, Category category) {
    Course course = null;
    try {
      if (category == null)
        return null;
      else if (courseIsFrozen(category.getCourse())) {
        return null;
      } else if (transactions.removeCategory(p, category)) {
        return cat.getCourse();
      } else {
        return null;
      }
    } catch (Exception e) {
      System.out.println("Database failed to find content with id "
          + category);
    }
    return course;
  }

  /**
   * Remove a Net from the list of those given CMS admin access
   * 
   * @return TransactionResult
   */
  public TransactionResult removeCMSAdmin(User p, User toRemove) {
    TransactionResult result = new TransactionResult();
    boolean success = false;
    try {
      if (!p.isCMSAdmin())
        result.addError("Couldn't find CMS admin in database");
      else success = transactions.removeCMSAdmin(p);
    } catch (Exception e) {
      result.addError("Database failed to remove CMS admin");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * @return TransactionResult
   */
  public TransactionResult removeCtgRow(User p, CategoryRow row) {
    TransactionResult result = new TransactionResult();
    try {
      if (row == null)
        result.addError("Couldn't find content row with id: " + row);
      else {
        Category cat = row.getCategory();
            database.categoryHome().findByPrimaryKey(
                new CategoryPK(row.getCategory()));
        if (cat == null)
          result.addError("Couldn't find content with id "
              + row.getCategory());
        else if (courseIsFrozen(cat.getCourse()))
          result.addError("Course is frozen; no changes may be made to it");
        else if (transactions.removeCtgRow(p, row)) {
          return result;
        }
      }
    } catch (Exception e) {
      result.addError("Database failed to find row with id " + row);
    }
    return result;
  }

  public TransactionResult removeExtension(User p, Course course, Group group) {
    TransactionResult result = new TransactionResult();
    try {
      Assignment assign = group.getAssignment();
      /*
       * Authentication happens by Course, must check that the Group is
       * actually a group within the given course.
       */
      if (course != assign.getCourse()) {
        result.addError("Illegal request: group is not in this course");
      }
      if (courseIsFrozen(assign.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
      }
      if (!result.hasErrors()) {
        result = transactions.removeExtension(p, group);
      }
    } catch (Exception e) {
      result.addError("Unexpected error; could not remove extension");
      e.printStackTrace();
    }
    return result;
  }

  public TransactionResult restoreAnnouncement(User p, Announcement announce) {
    TransactionResult result = new TransactionResult();
    try {
      if (announce == null) {
        result.addError("Announcement does not exist in database");
      } else if (courseIsFrozen(announce.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
      }
      if (!result.hasErrors()) {
        result = transactions.restoreAnnouncement(p, announce);
      }
    } catch (Exception e) {
      result.addError("Unexpected error; could not restore announcement");
      e.printStackTrace();
    }
    return result;
  }

  public TransactionResult restoreAssignment(User p, Assignment assignment) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(assignment.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
      } else {
        result = transactions.restoreAssignment(p, assignment);
      }
    } catch (Exception e) {
      result.addError("Unexpected error while trying to restore assignment");
      e.printStackTrace();
    }
    return result;
  }

  public TransactionResult sendEmail(User p, Course course, HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    try {
      Emailer email = new Emailer();
      DiskFileUpload upload = new DiskFileUpload();
      Iterator i = upload.parseRequest(request).iterator();
      String subject = null, body = null, recipient = null, netid = null;
      while (i.hasNext()) {
        FileItem item = (FileItem) i.next();
        if (item.getFieldName().equals(AccessController.P_EMAIL_SUBJECT)) {
          subject = item.getString();
        } else if (item.getFieldName().equals(AccessController.P_EMAIL_BODY)) {
          body = item.getString();
        } else if (item.getFieldName().equals(
            AccessController.P_EMAIL_RECIPIENTS)) {
          recipient = item.getString();
        } else if (item.getFieldName().equals(AccessController.P_EMAIL_NETIDS)) {
          netid = item.getString();
          email.addTo(netid + "@cornell.edu");
        }
      }
      if (recipient.equals("all") || recipient.equals("students")) {
        Iterator students =
            database.studentHome().findByCourseSortByLastName(course)
                .iterator();
        while (students.hasNext()) {
          Student student = (Student) students.next();
          email.addTo(student.getNet() + "@cornell.edu");
        }
      }
      // The decision was made that staff should always receive the email
      // if (recipient.equals("all") || recipient.equals("staff")) {
      Iterator staffs =
          database.staffHome().findByCourse(course).iterator();
      while (staffs.hasNext()) {
        Staff staff = (Staff) staffs.next();
        email.addTo(staff.getNet() + "@cornell.edu");
      }
      // }
      String fullname = p.getFirstName();
      fullname +=
          fullname.length() > 0 ? " " + p.getLastName() : p.getLastName();
      email.setFrom("\"" + fullname + "\"" + "<" + p.getNet()
          + "@cornell.edu" + ">");
      Course course =
          database.courseHome().findByPrimaryKey(new CoursePK(course));
      email.setSubject("[" + course.getDisplayedCode() + "] " + subject);
      email.setMessage(body);
      email.setRecipient(recipient);
      result = transactions.sendEmail(p, course, email);
    } catch (Exception e) {
      e.printStackTrace();
      result.addError("Unexpected error; could not send e-mail");
    }
    return result;
  }

  /**
   * Set the properties of an existing assignment or create a new assignment
   * 
   * @param p
   * @param assign
   *           of the assignment to set, or 0 to create a new one
   * @param request
   *          The HTTP request from which to take parameters and values
   * @return TransactionResult
   */
  public TransactionResult setAssignmentProps(User p, Course course,
      Assignment assign, HttpServletRequest request) {
    Profiler.enterMethod("TransactionHandler.setAssignmentProps",
        "Assignment: " + assign.toString());
    TransactionResult result = new TransactionResult();
    String name = "", nameshort = "", status = "", description = "";
    String duedate = null, duetime = null, dueampm = null, latedate = null, latetime =
        null, lateampm = null, regradedate = null, regradetime = null, regradeampm =
        null, tslockdate = null, tslocktime = null, tslockampm = null;
    Date due = null, late = null, regradedeadline = null;
    boolean latesubmissions = false, assignedgroups = false, assignedgraders =
        false, studentregrades = false, showstats = false, showsolution = false;
    int graceperiod = 0, groupmin = 0, groupmax = 0, groupoption = 0, regradeoption =
        0, numOfAssignedFiles = 0, type = Assignment.ASSIGNMENT;
    float score = 0, weight = 0;
    int order = 1;
    char letter = 'a';
    SubProblemOptions choices = null;
    boolean useSchedule = false; // flag indicating whether scheduling is in
                                  // use
    boolean emptyProbName = false, emptyItemName = false, emptySubmissionName =
        false; // flag indicating empty names, so only one such error is shown
    boolean emptyQuestName = false; // flag indicating an empty problem name was
                                    // submitted
    String TSTimeStr = null; // string indicating the duration of a timeslot in
                              // h:mm:ss format
    long TSDuration = 0; // duration of a timeslot in seconds
    int TSMaxGroups = 0; // maximum number of groups in a timeslot
    Date TSLockedTime = null; // deadline for students to change slots
    boolean proceed = true; // success flag (not an assignment property)
    long importID = 0;
    DiskFileUpload upload = new DiskFileUpload();
    AssignmentOptions options = new AssignmentOptions();
    HashSet filenames  = new HashSet(),
            probnames  = new HashSet(),
            subnames   = new HashSet(),
            questnames = new HashSet();
    HashMap probScores = new HashMap(); // for ensuring totalscore = sum of
                                        // problem scores
    List info = null;
    try {
      info =
          upload.parseRequest(request, 1024, AccessController.maxFileSize,
              FileUtil.TEMP_DIR);
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
        result.setValue(info);
        return result;
      }
      Iterator i = info.iterator();
      while (i.hasNext()) {
        FileItem item = (FileItem) i.next();
        String field = item.getFieldName();
        if (item.isFormField()) {
          if (field.equals(AccessController.P_NAME)) {
            name = item.getString();
          } else if (field.equals(AccessController.P_NAMESHORT)) {
            nameshort = item.getString();
          } else if (field.equals(AccessController.P_ASSIGNIDMENTTYPE)) {
            type = Integer.parseInt(item.getString());
            options.setAssignmentType(type);
          } else if (field.equals(AccessController.P_DUEDATE)) {
            duedate = item.getString();
          } else if (field.equals(AccessController.P_DUETIME)) {
            duetime = item.getString();
          } else if (field.equals(AccessController.P_DUEAMPM)) {
            dueampm = item.getString();
          } else if (field.equals(AccessController.P_GRACEPERIOD)) {
            try {
              graceperiod = Integer.parseInt(item.getString());
            } catch (NumberFormatException nfe) {
              result.addError("Grace period must be an integer");
              proceed = false;
            }
          } else if (field.equals(AccessController.P_LATEALLOWED)) {
            latesubmissions = item.getString().equals(AccessController.ONE);
          } else if (field.equals(AccessController.P_LATEDATE)) {
            latedate = item.getString();
          } else if (field.equals(AccessController.P_LATETIME)) {
            latetime = item.getString();
          } else if (field.equals(AccessController.P_LATEAMPM)) {
            lateampm = item.getString();
          } else if (field.equals(AccessController.P_USESCHEDULE)) {
            try {
              useSchedule = item.getString().equals("on"); // changed to
                                                            // support "on"
                                                            // checkbox value,
                                                            // EPW 02-24-06
            } catch (Exception e) {
              useSchedule = true;
            }
          } else if (field.equals(AccessController.P_TSDURATIONSTR)) {
            TSTimeStr = item.getString();
          } else if (field.equals(AccessController.P_TSMAXGROUPS)) {
            // Try-catch block added in case empty string sent, EPW 02-24-06
            try {
              TSMaxGroups = Integer.parseInt(item.getString());
            } catch (NumberFormatException nfe) {
              TSMaxGroups = 0;
            }
          } else if (field.equals(AccessController.P_SCHEDULE_LOCKDATE)) {
            tslockdate = item.getString();
          } else if (field.equals(AccessController.P_SCHEDULE_LOCKTIME)) {
            tslocktime = item.getString();
          } else if (field.equals(AccessController.P_SCHEDULE_LOCKAMPM)) {
            tslockampm = item.getString();
          } else if (field.equals(AccessController.P_STATUS)) {
            status = item.getString();
          } else if (field.equals(AccessController.P_DESCRIPTION)) {
            description = item.getString();
          } else if (field.equals(AccessController.P_GROUPS)) {
            groupoption = Integer.parseInt(item.getString());
          } else if (field.equals(AccessController.P_GROUPSMIN)) {
            try {
              groupmin = Integer.parseInt(item.getString());
            } catch (NumberFormatException nfe) {
              groupmin = -1;
            }
          } else if (field.equals(AccessController.P_GROUPSMAX)) {
            try {
              groupmax = Integer.parseInt(item.getString());
            } catch (NumberFormatException nfe) {
              groupmax = -1;
            }
          } else if (field.equals(AccessController.P_GROUPSBYTA)) {
            assignedgraders = item.getString().equals(AccessController.ONE);
          } else if (field.equals(AccessController.P_REGRADES)) {
            regradeoption = Integer.parseInt(item.getString());
          } else if (field.equals(AccessController.P_REGRADEDATE)) {
            regradedate = item.getString();
          } else if (field.equals(AccessController.P_REGRADETIME)) {
            regradetime = item.getString();
          } else if (field.equals(AccessController.P_REGRADEAMPM)) {
            regradeampm = item.getString();
          } else if (field.equals(AccessController.P_TOTALSCORE)) {
            try {
              score = StringUtil.parseFloat(item.getString());
              if (score <= 0) throw new NumberFormatException();
            } catch (NumberFormatException nfe) {
              result.addError("Max score must be a positive number");
              proceed = false;
            }
          } else if (field.equals(AccessController.P_WEIGHT)) {
            try {
              weight = StringUtil.parseFloat(item.getString());
              if (weight < 0) throw new NumberFormatException();
            } catch (NumberFormatException nfe) {
              result.addError("Weight must be a positive number");
              proceed = false;
            }
          } else if (field.equals(AccessController.P_SHOWSTATS)) {
            showstats = true;
          } else if (field.equals(AccessController.P_SHOWSOLUTION)) {
            showsolution = true;
          } else if (field.equals(AccessController.P_GROUPSFROM)) {
            importID = Long.parseLong(item.getString());
          } else if (field.startsWith(AccessController.P_REQFILENAME)) {
            long id =
                Long
                    .parseLong((field.split(AccessController.P_REQFILENAME))[1]);
            if (subnames.contains(item.getString())) {
              result.addError("A required submission with name '"
                  + item.getString() + "' already exists");
              proceed = false;
            } else {
              if (item.getString().equals("")) {
                emptySubmissionName = true;
              } else {
                subnames.add(item.getString());
                options.addRequiredFileName(item.getString(), id);
                numOfAssignedFiles++;
              }
            }
          } else if (field.startsWith(AccessController.P_REQFILETYPE)) {
            long id =
                Long
                    .parseLong((field.split(AccessController.P_REQFILETYPE))[1]);
            options.addRequiredFileType(item.getString(), id);
          } else if (field.startsWith(AccessController.P_REQSIZE)) {
            long id =
                Long.parseLong((field.split(AccessController.P_REQSIZE))[1]);
            try {
              int size = Integer.parseInt(item.getString());
              if (size <= 0) throw new NumberFormatException();
              options.addRequiredFileMaxSize(size, id);
            } catch (NumberFormatException e) {
              result.addError("Max submission size must be a positive integer");
              proceed = false;
            }
          } else if (field.startsWith(AccessController.P_NEWREQFILENAME)) {
            int id =
                Integer.parseInt((field
                    .split(AccessController.P_NEWREQFILENAME))[1]);
            if (subnames.contains(item.getString())) {
              result.addError("A required submission with name '"
                  + item.getString() + "' already exists");
              proceed = false;
            } else {
              if (item.getString().equals("")) {
                emptySubmissionName = true;
              } else {
                subnames.add(item.getString());
                options.addNewRequiredFileName(item.getString(), id);
                numOfAssignedFiles++;
              }
            }
          } else if (field.startsWith(AccessController.P_NEWREQFILETYPE)) {
            int id =
                Integer.parseInt((field
                    .split(AccessController.P_NEWREQFILETYPE))[1]);
            options.addNewRequiredFileType(item.getString(), id);
          } else if (field.startsWith(AccessController.P_NEWREQSIZE)) {
            int id =
                Integer
                    .parseInt((field.split(AccessController.P_NEWREQSIZE))[1]);
            try {
              int size = Integer.parseInt(item.getString());
              if (size <= 0) throw new NumberFormatException();
              options.addNewRequiredMaxSize(size, id);
            } catch (NumberFormatException e) {
              result.addError("Max submission size must be a positive integer");
              proceed = false;
            }

          } else if (field.startsWith(AccessController.P_NEWITEMNAME)) {
            int id =
                Integer
                    .parseInt((field.split(AccessController.P_NEWITEMNAME))[1]);
            if (filenames.contains(item.getString())) {
              result.addError("An assignment file with name '"
                  + item.getString() + "' already exists");
              proceed = false;
            } else {
              if (item.getString().equals("")) {
                emptyItemName = true;
              } else {
                filenames.add(item.getString());
                options.addNewAssignmentItemName(item.getString(), id);
              }
            }
          } else if (field.startsWith(AccessController.P_ITEMNAME)) {
            long id =
                Long.parseLong((field.split(AccessController.P_ITEMNAME))[1]);
            if (filenames.contains(item.getString())) {
              result.addError("An assignment file with name '"
                  + item.getString() + "' already exists");
              proceed = false;
            } else {
              if (item.getString().equals("")) {
                emptyItemName = true;
              } else {
                filenames.add(item.getString());
                options.addAssignmentItemName(item.getString(), id);
              }
            }
          } else if (field.startsWith(AccessController.P_REMOVEITEM)) {
            long id =
                Long.parseLong((field.split(AccessController.P_REMOVEITEM))[1]);
            options.removeAssignmentItem(id);
          } else if (field.startsWith(AccessController.P_RESTOREREQ)) {
            long id =
                Long.parseLong((field.split(AccessController.P_RESTOREREQ))[1]);
            options.restoreSubmission(id);
            numOfAssignedFiles++;
          } else if (field.startsWith(AccessController.P_RESTOREITEM)) {
            /*
             * XXX This next line breaks eclipse's Content Assist for me for
             * some unfathomable reason. When it's commented out, everything is
             * fine. Any type of new variable declaration has the same effect as
             * long as it's in this exact position in the code -Jon
             */
            long id =
                Long
                    .parseLong((field.split(AccessController.P_RESTOREITEM))[1]);
            options.restoreAssignmentItem(id);
          } else if (field.startsWith(AccessController.P_RESTOREITEMFILE)) {
            String itemfile =
                field.split(AccessController.P_RESTOREITEMFILE)[1];
            String[] itemfiles = itemfile.split("_");
            long item = Long.parseLong(itemfiles[0]);
            long file = Long.parseLong(itemfiles[1]);
            try {
              options.restoreAssignmentFile(item, file);
            } catch (FileUploadException e) {
              result.addError(e.getMessage());
              proceed = false;
            }
          } else if (field.startsWith(AccessController.P_REMOVEREQ)) {
            options.removeSubmission(Long.parseLong(field
                .split(AccessController.P_REMOVEREQ)[1]));
            numOfAssignedFiles--;
          } else if (field.equals(AccessController.P_REMOVESOL)) {
            options.removeCurrentSolutionFile();
          } else if (field.startsWith(AccessController.P_RESTORESOL)) {
            long sol =
                Long.parseLong(field.split(AccessController.P_RESTORESOL)[1]);
            try {
              options.restoreSolutionFile(sol);
            } catch (FileUploadException e) {
              result.addError(e.getMessage());
              proceed = false;
            }
          } else if (field.startsWith(AccessController.P_SUBPROBNAME)) {
            long sub =
                Long.parseLong(field.split(AccessController.P_SUBPROBNAME)[1]);
            /*
             * if (probnames.contains(item.getString())) { // if there exists
             * another subporblem with the same name result.addError("A
             * subproblem with name '" + item.getString() + "' already exists");
             * proceed= false; } else
             */if (item.getString().equals("")) {
              emptyProbName = true;
            } else {
              // Assign subproblem orders in the order that they appear in the
              // form
              options.addSubProblemOrder(order, sub);
              options.addSubProblemName(item.getString(), sub);
              probnames.add(item.getString());
              order++;

              // reset the choice lettering
              letter = 'a';
              choices = new SubProblemOptions();
              options.addSubProblemChoices(choices, sub);
            }
          } else if (field.startsWith(AccessController.P_SUBPROBSCORE)) {
            long sub =
                Long.parseLong(field.split(AccessController.P_SUBPROBSCORE)[1]);
            float maxscore = 0.0f;
            try {
              maxscore = StringUtil.parseFloat(item.getString());
              if (maxscore < 0.0f) throw new NumberFormatException();
            } catch (NumberFormatException e) {
              result.addError("Problem scores must be positive numbers");
              proceed = false;
            }
            Long key = new Long(sub);
            if (!probScores.containsKey(key)) {
              probScores.put(key, new Float(maxscore));
            }
            options.addSubProblemScore(maxscore, sub);
          } else if (field.startsWith(AccessController.P_REMOVECHOICE)) {
            long choice =
                Long.parseLong(field.split(AccessController.P_REMOVECHOICE)[1]);
            choices.removeChoice(choice);
          } else if (field.startsWith(AccessController.P_NEWSUBPROBNAME)) {
            int subProblem =
                Integer
                    .parseInt(field.split(AccessController.P_NEWSUBPROBNAME)[1]);
            if (probnames.contains(item.getString())) {
              result.addError("A subproblem with name '" + item.getString() + "' already exists");
              proceed = false;
            } else if (item.getString().equals("")) {
              emptyProbName = true;
            } else {
              // Assign subproblem orders in the order that they appear in the
              // form
              options.addNewSubProblemName(item.getString(), subProblem);
              options.addNewSubProblemOrder(order, subProblem);
              probnames.add(item.getString());
              order++;

              // reset the choice lettering
              letter = 'a';
              choices = new SubProblemOptions();
              options.addNewSubProblemChoices(choices, subProblem);
            }
          } else if (field.startsWith(AccessController.P_NEWSUBPROBSCORE)) {
            int subProblem =
                Integer.parseInt(field
                    .split(AccessController.P_NEWSUBPROBSCORE)[1]);
            float maxscore = 0;
            try {
              maxscore = StringUtil.parseFloat(item.getString());
              if (maxscore < 0.0f) throw new NumberFormatException();
              probScores.put(new Long(-subProblem), new Float(maxscore));
            } catch (NumberFormatException e) {
              result.addError("Problem scores must be positive numbers");
              proceed = false;
            }
            options.addNewSubProblemScore(maxscore, subProblem);
          } else if (field.startsWith(AccessController.P_RESTORESUBPROB)) {
            long sub =
                Long
                    .parseLong(field.split(AccessController.P_RESTORESUBPROB)[1]);
            options.restoreSubProblem(sub);
          } else if (field.startsWith(AccessController.P_REMOVESUBPROB)) {
            long sub =
                Long
                    .parseLong(field.split(AccessController.P_REMOVESUBPROB)[1]);
            probScores.put(new Long(sub), new Float(0));
            options.removeSubProblem(sub);
          }
          // Surveys
          else if (field.startsWith(AccessController.P_CORRECTCHOICE)) {
            int answer =
                Integer
                    .parseInt(field.split(AccessController.P_CORRECTCHOICE)[1]);
            int correctChoice = -1;
            try {
              correctChoice = Integer.parseInt(item.getString());
              if (correctChoice < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
              result.addError("Correct choices must be positive numbers");
              proceed = false;
            }
            options.addSubProblemAnswer(correctChoice, answer);
          } else if (field.startsWith(AccessController.P_SUBPROBTYPE)) {
            long quest =
                Long.parseLong(field.split(AccessController.P_SUBPROBTYPE)[1]);
            int questtype = -1;
            try {
              questtype = Integer.parseInt(item.getString());
              if (questtype < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
              result.addError("Question types must be positive numbers");
              proceed = false;
            }
            options.addSubProblemType(questtype, quest);
          } else if (field.startsWith(AccessController.P_CHOICE)) {
            String[] tokens = field.split("_");
            long quest = Long.parseLong(tokens[1]);
            long choice = Long.parseLong(tokens[2]);

            choices.addChoiceText(item.getString(), choice);
            choices.addChoiceLetter(Character.toString(letter), choice);
            letter++;
          } else if (field.startsWith(AccessController.P_NEWCORRECTCHOICE)) {
            int answer =
                Integer.parseInt(field
                    .split(AccessController.P_NEWCORRECTCHOICE)[1]);
            int correctChoice = -1;
            try {
              correctChoice = Integer.parseInt(item.getString());
              if (correctChoice < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
              result.addError("Correct choices must be positive numbers");
              proceed = false;
            }
            options.addNewSubProblemAnswer(correctChoice, answer);
          } else if (field.startsWith(AccessController.P_NEWSUBPROBTYPE)) {
            int type =
                Integer
                    .parseInt(field.split(AccessController.P_NEWSUBPROBTYPE)[1]);
            int questtype = -1;
            try {
              questtype = Integer.parseInt(item.getString());
              if (questtype < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
              result.addError("Question types must be positive numbers");
              proceed = false;
            }
            options.addNewSubProblemType(questtype, type);
          } else if (field.startsWith(AccessController.P_NEWSUBPROBORDER)) {
            int questorder = -1;
            try {
              questorder = Integer.parseInt(item.getString());
              if (questorder < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
              result.addError("Question orders must be positive numbers");
              proceed = false;
            }
          } else if (field.startsWith(AccessController.P_NEWCHOICE)) {

            String[] tokens = field.split("_");

            int quest = Integer.parseInt(tokens[1]);
            int choice = Integer.parseInt(tokens[2]);
            choices.addNewChoiceText(item.getString(), choice);
            choices.addNewChoiceLetter(Character.toString(letter), choice);
            letter++;
          } else {
            System.out.println("Not parsed: " + item.getString());
          }
        } else if (!item.getName().equals("")) { // file to be downloaded
          if (field.equals(AccessController.P_SOLFILE)) {
            options.addSolutionFile(item);

          } else if (field.startsWith(AccessController.P_NEWITEMFILE)) {
            int id =
                Integer.parseInt(item.getFieldName().split(
                    AccessController.P_NEWITEMFILE)[1]);
            options.addNewAssignmentFile(item, id);
          } else if (field.startsWith(AccessController.P_ITEMFILE)) {
            long id =
                Long.parseLong(item.getFieldName().split(
                    AccessController.P_ITEMFILE)[1]);
            options.addReplacementAssignmentFile(item, id);
          } else {
            System.out.println("Not parsed (file): " + field);
          }
        }
      }
      if (name == null || name.equals("")) {
        result.addError("Name must be non-empty");
      }
      if (nameshort == null || nameshort.equals("")) {
        result.addError("Name short must be non-empty");
      }
      if (duedate == null || duetime == null || dueampm == null)
        result.addError("Due date is not in the proper format");
      else {
        try {
          due = DateTimeUtil.parseDate(duedate, duetime, dueampm);
        } catch (ParseException pe) {
          result.addError("Due date is not in the proper format");
          proceed = false;
        } catch (IllegalArgumentException iae) {
          result.addError("Due date " + iae.getMessage());
          proceed = false;
        }
      }
      if (emptyProbName) {
        result.addError("Problem names cannot be empty");
        proceed = false;
      }
      if (emptyItemName) {
        result.addError("Assignment file names must not be empty");
      }
      if (emptySubmissionName) {
        result.addError("Required submission names must not be empty");
      }
      if (latesubmissions) {
        if (latedate == null || latetime == null || lateampm == null)
          result
              .addError("Late submission deadline is not in the proper format");
        else {
          try {
            late = DateTimeUtil.parseDate(latedate, latetime, lateampm);
          } catch (ParseException pe) {
            result
                .addError("Late submission deadline is not in the proper format");
            proceed = false;
          } catch (IllegalArgumentException iae) {
            result.addError("Late submission deadline " + iae.getMessage());
            proceed = false;
          }
        }
      }
      if (useSchedule) {
        if (TSTimeStr == null)
          result.addError("Timeslot duration is not in the proper format");
        else {
          try {
            TSDuration = Long.parseLong(TSTimeStr);
          } catch (Exception e) {
            result.addError("Timeslot duration is not in the proper format");
          }
        }
        if (tslockdate.equals("") || tslocktime.equals("")) {
          TSLockedTime = null; // empty input means no deadline to set schedule
        } else {
          try {
            TSLockedTime =
                DateTimeUtil.parseDate(tslockdate, tslocktime, tslockampm);
          } catch (ParseException pe) {
            result
                .addError("Schedule change deadline is not in the proper format");
            proceed = false;
          } catch (IllegalArgumentException iae) {
            result.addError("Schedule change deadline " + iae.getMessage());
            proceed = false;
          }
        }
      }
      if (groupoption == 0) {
        groupmin = 1;
        groupmax = 1;
      } else if (groupoption == 2) {
        groupmin = 0;
        groupmax = 0;
        assignedgroups = true;
      } else if (groupoption == 1) {
        if (groupmin < 1) {
          result.addError("Minimum group size must be a positive integer");
          proceed = false;
        }
        if (groupmax < 1) {
          result.addError("Maximum group size must be a positive integer");
          proceed = false;
        }
        if (!(groupmin <= groupmax)) {
          result.addError("Please specify a valid group size range");
          proceed = false;
        }
      }
      if (regradeoption == 0) {
        studentregrades = false;
      } else if (regradeoption == 1) {
        studentregrades = true;
        if (regradedate == null || regradetime == null || regradeampm == null)
          result
              .addError("Regrade submission deadline is not in the proper format");
        else {
          try {
            regradedeadline =
                DateTimeUtil.parseDate(regradedate, regradetime, regradeampm);
          } catch (ParseException pe) {
            result
                .addError("Regrade submission deadline is not in the proper format");
            proceed = false;
          } catch (IllegalArgumentException iae) {
            result.addError("Regrade submission deadline " + iae.getMessage());
            proceed = false;
          }
        }
      }
      Iterator pScores = probScores.values().iterator();
      float total = 0.0f;
      while (pScores.hasNext()) {
        Float val = (Float) pScores.next();
        total += val.floatValue();
      }
      // don't check this for quizzes for now
      if (probScores.size() > 0 && total != score
          && type != Assignment.QUIZ) {
        result.addError("Problem scores sum (" + total
            + ") does not equal the Total Score (" + score + ")");
      }
      Course course =
          database.courseHome().findByPrimaryKey(new CoursePK(course));
      long item = -1;
      int ob = -1;
      Iterator items = options.getReplacedAssignmentItems().iterator();
      Iterator s = options.getNewAssignmentItems().iterator();
      while (items.hasNext() || s.hasNext()
          || options.hasUncheckedSolutionFile()) {
        try {
          boolean oldFile = items.hasNext(); // item > 0;
          boolean solFile = !oldFile && !s.hasNext(); //  < 0;
          FileItem data;
          String fileName = null;
          String[] givenName;
          if (solFile) {
            data = options.getSolutionFile();
            givenName =
                FileUtil.splitFileNameType(FileUtil
                    .trimFilePath(data.getName()));
            fileName = givenName[0];
          } else if (oldFile) {
            item = ((Long) items.next()).longValue();
            data = options.getFileItemBy(item);
            fileName = options.getItemNameBy(item);
            givenName =
                data == null ? null : FileUtil.splitFileNameType(FileUtil
                    .trimFilePath(data.getName()));
          } else {
            ob = ((Integer) s.next()).intValue();
            data = options.getNewFileItemBy(ob);
            fileName = options.getNewItemNameBy(ob);
            givenName =
                data == null ? null : FileUtil.splitFileNameType(FileUtil
                    .trimFilePath(data.getName()));
          }
          if (data == null && (fileName == null || fileName.equals(""))) {
            continue;
          } else if (fileName == null || fileName.equals("")) {
            throw new FileUploadException(
                "Must provide a name for assignment file " + givenName[0]
                    + (givenName[1].equals("") ? "" : "." + givenName[1]));
          } else if (data == null) {
            throw new FileUploadException(
                "No file provided for assignment file " + fileName);
          }
          String givenFileName =
              fileName + (givenName[1].equals("") ? "" : ("." + givenName[1]));
          /*
           * Some browsers will return an entire path name with the file name,
           * so we trim that here
           */
          givenFileName = FileUtil.trimFilePath(givenFileName);
          long fileCounter;
          java.io.File path, file;
          fileCounter = transactions.getCourseFileCounter(course);
          if (solFile)
            file =
                new java.io.File(
                    FileUtil
                        .getSolutionFileSystemPath(
                            course.getSemester(),
                            course,
                            -1 /*
                                 * TODO use asgn  (doesn't matter until we
                                 * change the filesystem format)
                                 */,
                            fileCounter, givenFileName));
          else file =
              new java.io.File(
                  FileUtil
                      .getAssignmentFileSystemPath(
                          course.getSemester(),
                          course,
                          -1 /*
                               * TODO use asgn  (doesn't matter until we
                               * change the filesystem format)
                               */,
                          fileCounter, givenFileName));
          path = file.getParentFile();
          if (path.exists())
            throw new FileUploadException(
                "Failed to find a unique path on the file system");
          if (!path.mkdirs())
            throw new FileUploadException(
                "Failed to create new directory in the file system");
          if (!file.createNewFile())
            throw new FileUploadException(
                "Failed to create new file in file system");
          data.write(file);
          if (solFile) {
            options.addFinalSolutionFile(new SolutionFileData(0, 0,
                givenFileName, false, path.getAbsolutePath()));
          } else if (oldFile) {
            options.setReplacementFinalFileBy(item, new AssignmentFileData(
                0, item, givenFileName, null, false, path.getAbsolutePath()));
          } else {
            options.setNewFinalFileBy(ob, new AssignmentFileData(0, 0,
                givenFileName, null, false, path.getAbsolutePath()));
          }
        } catch (FileUploadException e) {
          result.addError(e.getMessage());
          proceed = false;
        }
      }
      if (importID != 0) {
        options.setGroupMigration(importID);
        try {
          Assignment importAssign =
              database.assignmentHome().findByAssignment(importID);
          if (importAssign.getCourse() != course) {
            result
                .addError("Cannot import groups: assignments are not in the same course");
            proceed = false;
          }
        } catch (FinderException e) {
          result
              .addError("Assignment used for importing groups does not exist");
          proceed = false;
        }
      }
      if (proceed && !result.hasErrors()) {
        AssignmentData data = new AssignmentData();
        data.setAssignment(assign);
        data.setCourse(course);
        data.setName(name);
        data.setNameShort(nameshort);
        data.setDescription(description);
        data.setDueDate(due);
        data.setGracePeriod(graceperiod);
        data.setAllowLate(latesubmissions);
        data.setLateDeadline(late);
        data.setStatus(status);
        data.setGroupSizeMax(groupmax);
        data.setGroupSizeMin(groupmin);
        data.setAssignedGroups(assignedgroups);
        data.setAssignedGraders(assignedgraders);
        data.setStudentRegrades(studentregrades);
        data.setRegradeDeadline(regradedeadline);

        // if score was not set, compute maxScore by adding all subproblem
        // scores
        if (score < 0.01)
          data.setMaxScore(options.getMaxScore());
        else data.setMaxScore(score);

        data.setWeight(weight);
        data.setAssignedGroups(assignedgroups);
        data.setShowStats(showstats);
        data.setShowSolution(showsolution);
        data.setNumOfAssignedFiles(numOfAssignedFiles);
        data.setScheduled(useSchedule);
        data.setDuration(new Long(TSDuration));
        data.setGroupLimit(new Integer(TSMaxGroups));
        data.setTimeslotLockTime(TSLockedTime);
        data.setType(type);
        if ((assign == 0)) { // new assignment
          result = transactions.createNewAssignment(p, data, options);
        } else {
          result = transactions.setAssignmentProps(p, data, options);
        }
      }
    } catch (FileUploadException e) {
      result.addError(FileUtil.checkFileException(e));
    } catch (Exception e) {
      e.printStackTrace();
      result.addError("Unexpected error while trying to "
          + (assign == 0 ? "create" : "edit") + " this assignment");
      result.setException(e);
    }
    Profiler.exitMethod("TransactionHandler.setAssignmentProps",
        "Assignment: " + assign);
    result.setValue(info);
    return result;
  }
  
  /**
   * Helper method for keeping track of created columns. Returns
   * created.get(key), creating it if it doesn't exist.
   */
  private CategoryColumn getNewColumn(Map/*String, Column*/ created, String key, Category parent) {
    CategoryColumn result = (CategoryColumn) created.get(key);
    if (result == null) {
      result = null; // TODO: new CategoryColumn(parent);
      created.put(key, result);
    }
    return result;
  }

  /**
   * Set properties for an existing category or create a category for the given
   * course
   * 
   * @param p
   * @param category
   *          0 to create a new category; else the  of the category to edit
   * @param course
   * @param request
   * @return TransactionResult
   */
  public TransactionResult createNEditCategory(User p, Course course, HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      
      Category category;
      boolean  newCategory = request.getParameter(AccessController.P_NEWCTGNAME) != null;
      if (newCategory)
        category = null; // TODO: new Category(course);
      else
        category = database.getCategory(request.getParameter(AccessController.P_CTGNAME));
      
      if (category == null) {
        result.addError("Invalid or missing category identifier");
        return result;
      }
      if (category.getCourse() != course) {
        result.addError("Cannot change the course of an existing category");
        return result;
      }

      Map/*String, CategoryColumn*/ newColumns = new HashMap();
      Iterator i = request.getParameterMap().entrySet().iterator();
      while (i.hasNext()) {
        Map.Entry item = (Map.Entry) i.next();
        String field = (String) item.getKey();
        String value = (String) item.getValue();
        System.out.println("createNEditCtg: item: " + field + "=" + value);
        if (field.equals(AccessController.P_NEWCTGNAME)) {
          category.setName(value);
        } else if (field.startsWith(AccessController.P_CTGNAME)) {
          String id = field.split(AccessController.P_CTGNAME)[1];
          category.setName(value);
        } else if (field.equals(AccessController.P_ORDER)) {
          category.setAscending(value.equals(Category.ASCENDING));
        } else if (field.equals(AccessController.P_COLSORTBY)) {
          if (value.startsWith(AccessController.P_PREFIX_NEW_CONTENT)) {
            value = value.split(AccessController.P_PREFIX_NEW_CONTENT)[1];
            category.setSortBy(getNewColumn(newColumns, value, category));
          } else {
            category.setSortBy(database.getCategoryColumn(value));
          }
        } else if (field.equals(AccessController.P_NUMSHOWITEMS)) {
          try {
            if (value.equals(""))
              category.setNumToShow(Category.SHOWALL);
            else category.setNumToShow(Integer.parseInt(value));
          } catch (NumberFormatException e) {
            e.printStackTrace();
            result.addError("Max items to show must be a positive number");
          }
        } else if (field.equals(AccessController.P_AUTHORZN)) {
          int authorzn = Integer.parseInt(value);
          category.setAuthLevel(authorzn);
        } else if (field.startsWith(AccessController.P_NEWCOLNAME)) {
          String id = (field.split(AccessController.P_NEWCOLNAME))[1];
          getNewColumn(newColumns, id, category).setName(value);
        } else if (field.startsWith(AccessController.P_NEWCOLTYPE)) {
          String id = (field.split(AccessController.P_NEWCOLTYPE))[1];
          getNewColumn(newColumns, id, category).setType(value);
        } else if (field.startsWith(AccessController.P_NEWCOLHIDDEN)) {
          String id = (field.split(AccessController.P_NEWCOLHIDDEN))[1];
          // if we're seeing this field at all, its value is equivalent to
          // true
          getNewColumn(newColumns, id, category).setHidden(true);
        } else if (field.startsWith(AccessController.P_NEWCOLPOSITION)) {
          String id = (field.split(AccessController.P_NEWCOLPOSITION))[1];
          getNewColumn(newColumns, id, category).setPosition(Integer.parseInt(value));
        } else if (field.startsWith(AccessController.P_COLNAME)) {
          String id = (field.split(AccessController.P_COLNAME))[1];
          database.getCategoryColumn(id).setName(value);
        } else if (field.startsWith(AccessController.P_COLPOSITION)) {
          String id = (field.split(AccessController.P_COLPOSITION)[1]);
          database.getCategoryColumn(id).setPosition(Integer.parseInt(value));
        } else if (field.startsWith(AccessController.P_COLHIDDEN)) {
          String id = (field.split(AccessController.P_COLHIDDEN)[1]);
          database.getCategoryColumn(id).setHidden(Boolean.parseBoolean(value));
        } else if (field.startsWith(AccessController.P_REMOVECOL)) {
          String id = (field.split(AccessController.P_REMOVECOL)[1]);
          database.getCategoryColumn(id).setRemoved(true);
        } else if (field.startsWith(AccessController.P_RESTORECOL)) {
          String id = (field.split(AccessController.P_RESTORECOL)[1]);
          database.getCategoryColumn(id).setRemoved(false);
        } else if (field.startsWith(AccessController.P_CTGPOSITION)) {
          category.setPosition(Integer.parseInt(value));
        } else if (field.startsWith(AccessController.P_NEWCTGPOSITION)) {
          category.setPosition(Integer.parseInt(value));
        } else if (field.startsWith(AccessController.P_REMOVECTG)) {
          category.setRemoved(true);
        } else if (field.startsWith(AccessController.P_RESTORECTG)) {
          category.setRemoved(false);
        }
      }
      if (newCategory)
        success = transactions.createCategory(p, category, option);
      else transactions.updateCategory(p, category);
      if (!success)
        result
            .addError("Unexpected error while trying to create/edit content properties");
    } catch (FileUploadException e) {
      result.addError(FileUtil.checkFileException(e));
    } catch (Exception e) {
      e.printStackTrace();
      result
          .addError("Unexpected error while trying to create/edit content properties");
    }
    return result;
  }

  /**
   * Set course properties and course staff member permissions; add and remove
   * staff members
   * 
   * @param course
   *          The  of the course to work on
   * @param map
   *          A map of various AccessController-defined property strings to
   *          String values. Current staff Nets not present in the map are
   *          removed from the course; current and new staff have permissions
   *          set here.
   * @return TransactionResult
   */
  public TransactionResult setCourseProps(User p, Course course, Map map) {
    TransactionResult result = new TransactionResult();
    try {
      boolean isFreeze = map.containsKey(AccessController.P_FREEZECOURSE);
      if (isFreeze && courseIsFrozen(course))
        result.addError("Course is frozen; no changes may be made to it");
      else {
        boolean isAdmin, hasAdmin = false;
        // put request data into a nicer form
        Vector staff2remove = new Vector();     // staff
        Hashtable staffPerms = new Hashtable(); // Staff ->
                                                // CourseAdminPermissions
        // remove current staff members and set remaining staff member
        // permissions
        Iterator iter = course.getStaff().iterator();
        while (iter.hasNext()) {
          Staff staff = ((Staff) iter.next());
          if (map.containsKey(staff.getUser().getNetID() + AccessController.P_REMOVE)) {
            // the staff should be removed
            if (staff.getUser().equals(p))
              result.addError("Cannot remove yourself as a staff member");
            else  
              staff2remove.add(staff);
          } else { // set staff permissions
            isAdmin = map.containsKey(staff.getUser().getNetID() + AccessController.P_ISADMIN);
            hasAdmin = hasAdmin || isAdmin;
            staffPerms.put(staff, new CourseAdminPermissions(isAdmin, map
                .containsKey(net + AccessController.P_ISASSIGNID), map
                .containsKey(net + AccessController.P_ISGROUPS), map
                .containsKey(net + AccessController.P_ISGRADES), map
                .containsKey(net + AccessController.P_ISCATEGORY)));
          }
        }
        // add new staff
        int i = 0;
        String[] newnetids =
            (String[]) map.get(AccessController.P_NEWNET + i);
        while (newnetids != null) {
          isAdmin = map.containsKey(AccessController.P_NEWADMIN + i);
          hasAdmin = hasAdmin || isAdmin;
          staffPerms.put(newnetids[0].toLowerCase().trim(),
              new CourseAdminPermissions(isAdmin, map
                  .containsKey(AccessController.P_NEWASSIGNID + i), map
                  .containsKey(AccessController.P_NEWGROUPS + i), map
                  .containsKey(AccessController.P_NEWGRADES + i), map
                  .containsKey(AccessController.P_NEWCATEGORY + i)));
          ++i;
          newnetids = (String[]) map.get(AccessController.P_NEWNET + i);
        }
        if (!hasAdmin) {
          result
              .addError("Must have at least one staff member with admin privilege");
        }
        if (result.hasErrors()) {
          return result;
        }
        // set course general properties
        CourseProperties generalProperties =
            new CourseProperties(
                ((String[]) map.get(AccessController.P_NAME))[0],
                ((String[]) map.get(AccessController.P_CODE))[0],
                ((String[]) map.get(AccessController.P_DISPLAYEDCODE))[0],
                /*
                 * note course description is now edited from main course page,
                 * so editing it is a separate action; see
                 * TransactionHandler::editCourseDescription()
                 * ((String[])map.get(AccessController.P_DESCRIPTION))[0],
                 */
                course.getDescription(),
                ((String[]) map.get(AccessController.P_FREEZECOURSEID)) != null,
                ((String[]) map.get(AccessController.P_FINALGRADES)) != null,
                ((String[]) map.get(AccessController.P_SHOWTOTALSCORES)) != null,
                ((String[]) map.get(AccessController.P_SHOWASSIGNIDWEIGHTS)) != null,
                ((String[]) map.get(AccessController.P_SHOWGRADER)) != null,
                ((String[]) map.get(AccessController.P_HASSECTION)) != null,
                ((String[]) map.get(AccessController.P_COURSEIDGUESTACCESS)) != null,
                ((String[]) map.get(AccessController.P_ASSIGNIDGUESTACCESS)) != null,
                ((String[]) map.get(AccessController.P_ANNOUNCEGUESTACCESS)) != null,
                ((String[]) map.get(AccessController.P_SOLUTIONGUESTACCESS)) != null,
                ((String[]) map.get(AccessController.P_COURSEIDCCACCESS)) != null,
                ((String[]) map.get(AccessController.P_ASSIGNIDCCACCESS)) != null,
                ((String[]) map.get(AccessController.P_ANNOUNCECCACCESS)) != null,
                ((String[]) map.get(AccessController.P_SOLUTIONCCACCESS)) != null);
        result =
            transactions.setAllCourseProperties(p, course, staff2remove,
                staffPerms, generalProperties);
      }
    } catch (Exception e) {
      result.addError("Error while trying to set course properties");
      e.printStackTrace();
    }
    return result;
  }

  public TransactionResult editCourseDescription(User p, Course course,
      String newDescription) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(course))
        result.addError("Course is frozen; no changes may be made to it");
      else {
        result.appendErrors(transactions.editCourseDescription(p, course,
            newDescription));
      }
    } catch (Exception x) {
      result
          .addError("Unexpected error while trying to set course description");
      x.printStackTrace();
    }
    return result;
  }

  /**
   * Set the active semester for future operations
   * 
   * @return TransactionResult
   */
  public TransactionResult setCurrentSemester(User p, Semester sem) {
    TransactionResult result = new TransactionResult();
    boolean success = true;
    try {
      success = transactions.setCurrentSemester(p, sem);
    } catch (Exception e) {
      success = false;
      result.addError("Database failed to set current semester");
      e.printStackTrace();
    }
    return result;
  }

  public TransactionResult setExtension(User p, Group group, HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    try {
      Assignment assign = group.getAssignment();
      if (courseIsFrozen(assign.getCourse())) {
        result.addError("Course is frozen; no changes may be made to it");
      }
      if (!result.hasErrors()) {
        String duedate = null, duetime = null, dueampm = null;
        Date extension = null;
        duedate = request.getParameter(AccessController.P_DUEDATE + group.toString());
        duetime = request.getParameter(AccessController.P_DUETIME + group.toString());
        dueampm = request.getParameter(AccessController.P_DUEAMPM + group.toString());
        if (duedate == null || duetime == null || dueampm == null)
          result.addError("Extension date is not in the proper format");
        else {
          try {
            extension = DateTimeUtil.parseDate(duedate, duetime, dueampm);
          } catch (ParseException pe) {
            result.addError("Extension date is not in the proper format");
          } catch (IllegalArgumentException iae) {
            result.addError("Extension date is not in the proper format");
          }
          result = transactions.setExtension(p, group, extension);
        }
      }
    } catch (Exception e) {
      result.addError("Unexpected error; could not grant extension");
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Remove one or more timeslots from the course schedule
   * 
   * @return TransactionResult
   */
  public TransactionResult deleteTimeSlots(User p, Assignment assignment,
      HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    try {
      Course course = assignment.getCourse();
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      ArrayList toDelete = new ArrayList();
      DiskFileUpload u = new DiskFileUpload();
      Iterator i = u.parseRequest(request).iterator();
      // iterate through request to find all fields of form deletetimeslot_##
      while (i.hasNext()) {
        FileItem item = (FileItem) i.next();
        String field = item.getFieldName();
        if (field.startsWith(AccessController.P_DELETETIMESLOT)) {
          TimeSlot ts = database.getTimeSlot(field.split(AccessController.P_DELETETIMESLOT)[1]);
          if (ts.getHidden())
            result.addError("Timeslot has already been deleted");
          else if (ts.getStaff().equals(p.getNet()) || p.isAdminPrivByAssignment(assignment))
              toDelete.add(ts);
          else
              result.addError("User lacks permission to remove selected timeslot");
        }
      }
      try {
        transactions.removeTimeSlots(p, assignment, toDelete);
      } catch (final Exception e) {
        result.addError("Database failed to delete selected time slots", e);
      }
    } catch (FileUploadException e) {
      result.addError(FileUtil.checkFileException(e));
    } catch (Exception e) {
      e.printStackTrace();
      result.addError("Unexpected error while trying to remvoe timeslot(s)");
    }
    return result;
  }

  /**
   * Create a block of timeslots
   * 
   * @return TransactionResult
   */
  public TransactionResult createTimeSlots(User p, Assignment assign,
      HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    try {
      Course course = assign.getCourse();
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      if (!assign.getScheduled()) {
        result.addError("Scheduling not enabled for this assignment");
        return result;
      }
      String name = null;
      String staff = null;
      String location = null;
      int multiplicity = 0;
      Date startTime = null;
      // raw info about start time; to be converted to timestamp
      String sDate = null;
      String sTime = null;
      String sAMPM = null;
      DiskFileUpload u = new DiskFileUpload();
      Iterator i = u.parseRequest(request).iterator();
      while (i.hasNext()) {
        FileItem item = (FileItem) i.next();
        String field = item.getFieldName();
        if (field.equals(AccessController.P_NEWTSNAME))
          name = item.getString();
        else if (field.equals(AccessController.P_NEWTSLOCATION))
          location = item.getString();
        else if (field.equals(AccessController.P_NEWTSSTAFF))
          staff = item.getString();
        else if (field.equals(AccessController.P_NEWTSMULTIPLICITY))
          multiplicity = Integer.parseInt(item.getString());
        else if (field.equals(AccessController.P_NEWTSSTARTDATE))
          sDate = item.getString();
        else if (field.equals(AccessController.P_NEWTSSTARTTIME))
          sTime = item.getString();
        else if (field.equals(AccessController.P_NEWTSSTARTAMPM))
          sAMPM = item.getString();
      }

      // parse the start time information into a timestamp
      if (sDate == null || sTime == null || sAMPM == null) {
        result.addError("Time slot start time is not in the proper format");
        return result;
      } else {
        try {
          startTime = DateTimeUtil.parseDate(sDate, sTime, sAMPM);
        } catch (ParseException pe) {
          result.addError("Timeslot start time is not in the proper format");
          return result;
        } catch (IllegalArgumentException iae) {
          result.addError("Timeslot start time " + iae.getMessage());
          return result;
        }
      }
      // store the information in a TimeSlotData structure containing the
      // timestamp of the
      // first timeslot in the block
      TimeSlot tsd =
          new TimeSlot((long) 0, assign, course, name, location, staff,
              startTime, false, 0);
      // perform transaction
      boolean success = transactions.createTimeSlots(p, tsd, multiplicity);
      if (!success) {
        result.addError("Database failed to add to schedule");
      }

    } catch (FileUploadException e) {
      result.addError(FileUtil.checkFileException(e));
    } catch (Exception e) {
      e.printStackTrace();
      result.addError("Unexpected error while trying to add to schedule");
    }
    return result;
  }

  public TransactionResult setFinalGrades(User p, Course course,
      HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    boolean success = true;
    DiskFileUpload upload = new DiskFileUpload();
    Iterator list = null;
    try {
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
        return result;
      }
      list = upload.parseRequest(request).iterator();
    } catch (Exception e) {
      success = false;
      e.printStackTrace();
    }
    Collection grades = new Vector();
    while (success && list.hasNext()) {
      FileItem item = (FileItem) list.next();
      if (item.getFieldName().startsWith(AccessController.P_FINALGRADE)) {
        String netid =
            item.getFieldName().split(AccessController.P_FINALGRADE)[1];
        String grade = item.getString();
        grades.add(new String[] { netid, grade });
      }
    }
    try {
      success = transactions.setFinalGrades(p, course, grades);
    } catch (Exception e) {
      e.printStackTrace();
      success = false;
    }
    if (!success) {
      result.addError("Database failed to accept updated final grades.");
    }
    return result;
  }

  public TransactionResult setStaffPrefs(User p, Course course,
      HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
      } else {
        Staff data = new Staff();
        data.setEmailDueDate(false);
        data.setEmailFinalGrade(false);
        data.setEmailNewAssign(false);
        data.setEmailAssignedTo(false);
        data.setEmailRequest(false);
        DiskFileUpload upload = new DiskFileUpload();
        Iterator i = upload.parseRequest(request).iterator();
        while (i.hasNext()) {
          FileItem item = (FileItem) i.next();
          String field = item.getFieldName();
          if (field.equals(AccessController.P_PREF_DATECHANGE)) {
            data.setEmailDueDate(true);
          } else if (field.equals(AccessController.P_PREF_NEWASSIGNID)) {
            data.setEmailNewAssign(true);
          } else if (field.equals(AccessController.P_PREF_FINALGRADES)) {
            data.setEmailFinalGrade(true);
          } else if (field.equals(AccessController.P_PREF_ASSIGNIDEDTO)) {
            data.setEmailAssignedTo(true);
          } else if (field.equals(AccessController.P_PREF_REGRADEREQUEST)) {
            data.setEmailRequest(true);
          }
        }
        result = transactions.setStaffPrefs(p, course, data);
      }
    } catch (Exception e) {
      e.printStackTrace();
      result
          .addError("Unexpected error while trying to set course preferences");
    }
    return result;
  }

  public TransactionResult setStudentPrefs(User p, Course course,
      HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    try {
      if (courseIsFrozen(course)) {
        result.addError("Course is frozen; no changes may be made to it");
      } else {
        Student data = buildStudent(request);
        result = transactions.setStudentPrefs(p, course, data);
      }
    } catch (Exception e) {
      e.printStackTrace();
      result
          .addError("Unexpected error while trying to set course preferences");
    }
    return result;
  }

  public TransactionResult setAllStudentPrefs(User p,
      HttpServletRequest request) {
    TransactionResult result = new TransactionResult();
    try {
      Student data = buildStudent(request);
      result = transactions.setAllStudentPrefs(p, data);
    } catch (Exception e) {
      e.printStackTrace();
      result
          .addError("Unexpected error while trying to set course preferences");
    }
    return result;
  }

  private Student buildStudent(HttpServletRequest request)
      throws FileUploadException {
    Student data = new Student();
    data.setEmailDueDate(request
        .getParameter(AccessController.P_PREF_DATECHANGE) != null);
    data
        .setEmailFile(request.getParameter(AccessController.P_PREF_FILESUBMIT) != null);
    data.setEmailFinalGrade(request
        .getParameter(AccessController.P_PREF_FINALGRADES) != null);
    data
        .setEmailGroup(request.getParameter(AccessController.P_PREF_INVITATION) != null);
    data.setEmailNewAssignment(request
        .getParameter(AccessController.P_PREF_NEWASSIGNID) != null);
    data.setEmailNewGrade(request
        .getParameter(AccessController.P_PREF_GRADERELEASE) != null);
    data.setEmailRegrade(request
        .getParameter(AccessController.P_PREF_GRADECHANGE) != null);
    data.setEmailTimeSlot(request
        .getParameter(AccessController.P_PREF_TIMESLOTCHANGE) != null);
    return data;
  }

  /**
   * Attempts to accept submitted files as sent through the given
   * HttpServletRequest from the user. Returns a string describing the error if
   * the transaction failed and was rolled back. Returns a null string on
   * success.
   * 
   * @param netid
   * @param request
   * @return TransactionResult
   */
  public TransactionResult submitFiles(User p, HttpServletRequest request) {
    Profiler.enterMethod("TransactionHandler.submitFiles", "");
    TransactionResult result = new TransactionResult();
    try {
      String assignmentid = request.getParameter(AccessController.P_ASSIGNID);
      Assignment assignment = database.getAssignment(assignmentid);
      if (courseIsFrozen(assignment.getCourse()))
        result.addError("Course is frozen; no changes may be made to it");
      else {
        Group group = assignment.findGroup(p);
        // Get current time while adjusting for grace period
        long graceperiod = assignment.getGracePeriod() * 60000;
        Date now = new Date(System.currentTimeMillis() - graceperiod);
        if (now.after(assignment.getDueDate()) &&
            (!assignment.getAllowLate() || now.after(assignment.getLateDeadline())) &&
            (group.getExtension() == null || now.after(group.getExtension()))) {
          result.addError("Submissions for this assignment are no longer being accepted");
        } else {
          boolean isLate =
              (group.getExtension() == null)
                  && assignment.getDueDate().before(now);
          Collection files = getUploadedFiles(p, request, isLate);
          result.addError(transactions.submitFiles(p, assignment, files));
        }
      }
    } catch (FileUploadException e) {
      if (e.getMessage().equalsIgnoreCase(FileUtil.SIZE_VIOLATION))
        result
            .addError("A submitted file violates the CMS maximum file size of "
                + AccessController.maxFileSize + " bytes");
      else if (e.getMessage().startsWith("match fail")) {
        String err = e.getMessage();
        String fileName = err.substring(err.indexOf(":") + 1);
        result.addError("File '" + fileName
            + "' failed to match an accepted type");
      } else if (e.getMessage().startsWith("size violation")) {
        String err = e.getMessage();
        String fileName = err.substring(err.indexOf(":") + 1);
        result.addError("File '" + fileName
            + "' violated the maximum size limitation");
      } else {
        result.addError(e.getizedMessage());
      }
    } catch (Exception e) {
      result.addError("Error accessing database; files could not be submitted");
    }
    Profiler.exitMethod("TransactionHandler.submitFiles", "");
    return result;
  }

  /**
   * Attempts to accept a survey HttpServletRequest from the user. Returns a
   * string describing the error if the transaction failed and was rolled back.
   * Returns a null string on success.
   * 
   * @param netid
   * @param request
   * @return TransactionResult
   */
  public TransactionResult submitSurvey(User p, HttpServletRequest request) {
    Profiler.enterMethod("TransactionHandler.submitFiles", "");
    TransactionResult result = new TransactionResult();
    List info = null;
    List answers = null;
    DiskFileUpload upload = new DiskFileUpload();
    try {
      String assignmentid = request.getParameter(AccessController.P_ASSIGNID);
      Assignment assignment = database.getAssignment(assignmentid);
      if (courseIsFrozen(assignment.getCourse()))
        result.addError("Course is frozen; no changes may be made to it");
      else {
        Group group = assignment.findGroup(p);

        // Get current time while adjusting for grace period
        long graceperiod = assignment.getGracePeriod() * 60000;
        Date now = new Date(System.currentTimeMillis() - graceperiod);
        if (now.after(assignment.getDueDate())
            && (!assignment.getAllowLate() || now.after(assignment.getLateDeadline()))
            && (group.getExtension() == null || now.after(group.getExtension()))) {
          result.addError("Submissions for this assignment are no longer being accepted");
        } else {
          info =
              upload.parseRequest(request, 1024, AccessController.maxFileSize,
                  FileUtil.TEMP_DIR);
          Iterator i = info.iterator();
          answers = new Vector();
          while (i.hasNext()) {
            FileItem item = (FileItem) i.next();
            String field = item.getFieldName();
            if (item.isFormField()) {
              if (field.startsWith(AccessController.P_SUBPROBNAME)) {
                long sub =
                    Long
                        .parseLong(field.split(AccessController.P_SUBPROBNAME)[1]);
                Answer answerData = new Answer(null, null, sub, item.getString());
                answers.add(answerData);
              }
            }
          }
          result.addError(transactions.submitSurvey(p, assignmentid, answers));
        }
      }
    } catch (FileUploadException e) {
      if (e.getMessage().equalsIgnoreCase(FileUtil.SIZE_VIOLATION))
        result
            .addError("A submitted file violates the CMS maximum file size of "
                + AccessController.maxFileSize + " bytes");
      else if (e.getMessage().startsWith("match fail")) {
        String err = e.getMessage();
        String fileName = err.substring(err.indexOf(":") + 1);
        result.addError("File '" + fileName
            + "' failed to match an accepted type");
      } else if (e.getMessage().startsWith("size violation")) {
        String err = e.getMessage();
        String fileName = err.substring(err.indexOf(":") + 1);
        result.addError("File '" + fileName
            + "' violated the maximum size limitation");
      } else {
        result.addError(e.getizedMessage());
      }
    } catch (Exception e) {
      result.addError("Error accessing database; files could not be submitted");
    }
    Profiler.exitMethod("TransactionHandler.submitFiles", "");
    return result;
  }

  /**
   * Output a CSV file with assignment names, max scores and weights for the
   * given course
   * 
   * @param p
   * @param course
   * @param out
   * @return TransactionResult
   */
  public TransactionResult exportGradingRubric(User p, Course course,
      OutputStream out) {
    TransactionResult result = new TransactionResult();
    try {
      CSVPrinter printer = new CSVPrinter(out);
      Collection asgns = course.getAssignments();
      List data = new ArrayList(); // a String[3] in each slot; temporary asgn
                                    // info storage
      Iterator i = asgns.iterator();
      while (i.hasNext()) {
        Assignment asgn = (Assignment) i.next();
        String[] info = new String[3];
        info[0] = asgn.getName();
        info[1] = String.valueOf(asgn.getMaxScore());
        info[2] = String.valueOf(asgn.getWeight());
        data.add(info);
      }
      String[] line = new String[1 + asgns.size()];
      // format data in columns instead of rows for easy Excel-ing
      line[0] = "Assignment Name";
      for (int j = 0; j < asgns.size(); j++)
        line[j + 1] = ((String[]) data.get(j))[0];
      printer.println(line);
      line[0] = "Max Score";
      for (int j = 0; j < asgns.size(); j++)
        line[j + 1] = ((String[]) data.get(j))[1];
      printer.println(line);
      line[0] = "Weight";
      for (int j = 0; j < asgns.size(); j++)
        line[j + 1] = ((String[]) data.get(j))[2];
      printer.println(line);
    } catch (Exception x) {
      x.printStackTrace();
      result.addError(x.getMessage());
    }
    return result;
  }

  /**
   * Output a CSV file containing all students in the class, with any grades
   * they may have for the given assignment, to the OutputStream. If the given
   * assignment has the property that staff may only grade groups they've been
   * explicitly assigned to, and p is not admin, only output grades for groups p
   * is assigned to. (Note other groups are output as well, but their grades
   * aren't.)
   * 
   * @param p
   * @param assignment
   * @param s
   * @return TransactionResult
   */
  public TransactionResult exportSingleAssignmentGradesTable(User p,
      Assignment assignment, OutputStream s) {
    TransactionResult result = new TransactionResult();
    try {
      Course course = assignment.getCourse();
      Collection subProbs = assignment.getSubProblems();
      Collection students = null, grades = null;
      if (assignment.getAssignedGraders()
          && !p.isAdminPrivByCourse(course)) {
        students =
            database.studentHome().findByAssignmentAssignedTo(assignment,
                p.getNet());
        grades =
            database.gradeHome().findMostRecentByAssignmentGrader(
                assignment, p.getNet());
      } else {
        // both students and grades are sorted by Net; grades only has entries
        // where the grade exists
        students =
            database.studentHome().findByCourseSortByNet(
                assignment.getCourse());
        grades =
            database.gradeHome().findMostRecentByAssignment(assignment);
      }
      CSVPrinter out = new CSVPrinter(s);
      int numSubprobs = subProbs.size();
      String[] firstRow;
      long[] subProbLocs = null;
      Iterator i = subProbs.iterator();
      Iterator j = grades.iterator();
      int count = 0;
      if (numSubprobs == 0) {
        firstRow = new String[2];
        firstRow[0] = "Net";
        firstRow[1] = "Grade";
      } else {
        firstRow = new String[2 + numSubprobs];
        subProbLocs = new long[1 + numSubprobs];
        firstRow[0] = "Net";
        while (i.hasNext()) {
          SubProblem subProb = (SubProblem) i.next();
          firstRow[1 + (count++)] = subProb.getSubProblemName();
          subProbLocs[count] = subProb.getSubProblem();
        }
        firstRow[firstRow.length - 1] = "Total";
      }
      out.println(firstRow);
      i = students.iterator();
      Grade grade = null;
      // No subproblems
      if (subProbLocs == null) {
        String[] thisRow = new String[2];
        if (j.hasNext()) {
          grade = (Grade) j.next();
        }
        while (i.hasNext()) {
          Student student = (Student) i.next();
          thisRow[0] = student.getNet();
          if (grade != null && grade.getNet().equals(thisRow[0])) {
            thisRow[1] = String.valueOf(grade.getGrade());
            if (j.hasNext()) grade = (Grade) j.next();
          } else // either we're not yet at the netid of this grade, or we're
                  // at a netid past all existing grades or there are no grades
          thisRow[1] = "";
          out.println(thisRow);
        }
      } else // Subproblems
      {
        while (i.hasNext()) {
          Student student = (Student) i.next();
          String[] thisRow = new String[firstRow.length];
          thisRow[0] = student.getNet();
          count = 1;
          Float totalScore = null;
          boolean nextRow = false;
          while (!nextRow && j.hasNext()) {
            if (grade == null) grade = (Grade) j.next();
            if (grade.getNet().equals(thisRow[0])) {
              if (grade.getSubProblem() == 0) {
                totalScore = grade.getGrade();
                grade = null;
              } else if (grade.getSubProblem() == subProbLocs[count]) {
                thisRow[count++] = String.valueOf(grade.getGrade());
                grade = null;
              } else {
                thisRow[count++] = "";
              }
            } else if (thisRow[0].compareTo(grade.getNet()) < 0) {
              for (int k = count; k < thisRow.length; k++) {
                if (k == thisRow.length - 1 && totalScore != null) {
                  thisRow[k] = totalScore.toString();
                } else {
                  thisRow[k] = "";
                }
              }
              nextRow = true;
            } else {
              grade = null;
            }
          }
          if (totalScore != null) {
            for (int k = count; k < thisRow.length; k++) {
              if (k == thisRow.length - 1) {
                thisRow[k] = totalScore.toString();
              } else {
                thisRow[k] = "";
              }
            }
          }
          out.println(thisRow);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      result.addError(e.getMessage());
    }
    return result;
  }

  /**
   * Output a CSV file containing Net and final grade columns for all students
   * in the given course to the OutputStream
   * 
   * @param course
   * @param s
   * @return
   */
  public TransactionResult exportFinalGradesTemplate(Course course,
      OutputStream s) {
    TransactionResult result = new TransactionResult();
    try {
      Collection students =
          database.studentHome().findByCourseSortByNet(course);
      CSVPrinter out = new CSVPrinter(s);
      String[] line = null;
      // header line
      line =
          new String[] { CSVFileFormatsUtil.NET,
              CSVFileFormatsUtil.FINAL_GRADE };
      out.println(line);
      // data lines
      Iterator i = students.iterator();
      while (i.hasNext()) {
        Student student = (Student) i.next();
        String grade = student.getFinalGrade();
        line = new String[] { student.getNet(), grade == null ? "" : grade };
        out.println(line);
      }
    } catch (Exception x) {
      result.addError(x.getMessage());
    }
    return result;
  }

  /**
   * Output a CSV file containing all students in the class with all grades for
   * all assignments for the course, as well as their current total grades, to
   * the OutputStream
   * 
   * @param p
   * @param assignment
   * @param s
   * @return TransactionResult
   */
  public TransactionResult exportGradesTable(XMLBuilder builder, User p, Course course,
      OutputStream s) {
    TransactionResult result = new TransactionResult();
    try {
      Document doc = builder.buildStudentsPage(p, course, false, false);
      // Element root = doc.createElement("root");
      // doc.appendChild(root);
      // ViewStudentsXMLBuilder.buildStudentsPage(course, doc);
      CSVPrinter out = new CSVPrinter(s);
      Element root = (Element) doc.getFirstChild();
      Element xCourse =
          XMLUtil.getFirstChildByTagName(root, XMLBuilder.TAG_COURSE);
      NodeList assignsNode =
          (xCourse.getElementsByTagName(XMLBuilder.TAG_ASSIGNMENTS).item(0))
              .getChildNodes();
      int aCount = assignsNode.getLength();
      Assignment[] assigns = new Assignment[aCount];
      String[] firstRow = new String[5 + aCount];
      firstRow[0] = "Last Name";
      firstRow[1] = "First Name";
      firstRow[2] = "Net";
      for (int i = 0; i < aCount; i++) {
        Element assign = (Element) assignsNode.item(i);
        firstRow[3 + i] = assign.getAttribute(XMLBuilder.A_NAMESHORT);
        assigns[i] =
            database.getAssignment(assign.getAttribute(XMLBuilder.A_ASSIGNID));
      }
      firstRow[3 + aCount] = "Total Score";
      firstRow[4 + aCount] = "Final Grade";
      out.println(firstRow);
      NodeList students =
          (root.getElementsByTagName(XMLBuilder.TAG_STUDENTS).item(0))
              .getChildNodes();
      for (int j = 0; j < students.getLength(); j++) {
        String[] thisRow = new String[firstRow.length];
        Element student = (Element) students.item(j);
        if (!student.getAttribute(XMLBuilder.A_ENROLLED).equals(
            Student.ENROLLED)) {
          continue;
        }
        thisRow[0] = student.getAttribute(XMLBuilder.A_LASTNAME);
        thisRow[1] = student.getAttribute(XMLBuilder.A_FIRSTNAME);
        thisRow[2] = student.getNodeName();
        for (int k = 0; k < aCount; k++) {
          Element grade =
              (Element) student.getElementsByTagName("id" + assignsNode[k]).item(
                  0);
          if (!grade.hasAttribute(XMLBuilder.A_SCORE))
            thisRow[3 + k] = "";
          else thisRow[3 + k] = grade.getAttribute(XMLBuilder.A_SCORE);
        }
        thisRow[3 + aCount] = student.getAttribute(XMLBuilder.A_TOTALSCORE);
        thisRow[4 + aCount] = student.getAttribute(XMLBuilder.A_FINALGRADE);
        out.println(thisRow);
      }
    } catch (Exception e) {
      result.addError(e.getMessage());
    }
    return result;
  }

  /**
   * Output a CSV file containing all students in the class with basic
   * information for each student including CUs and final grades to the
   * OutputStream
   * 
   * @param p
   * @param assignment
   * @param s
   * @return TransactionResult
   */
  public TransactionResult exportStudentInfoFinalGrades(Course course,
      OutputStream s) {
    TransactionResult result = new TransactionResult();
    try {
      // get lists of Users and Students, both sorted by Net
      Iterator users = database.userHome().findByCourse(course).iterator();
      Iterator students =
          database.studentHome().findByCourseSortByNet(course).iterator();
      CSVPrinter out = new CSVPrinter(s);
      HashMap nets = new HashMap();
      Vector output = new Vector();
      final int numCols =
          CSVFileFormatsUtil
              .getNumColumns(CSVFileFormatsUtil.FINALGRADES_FORMAT), lastnameCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT,
              CSVFileFormatsUtil.LASTNAME), firstnameCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT,
              CSVFileFormatsUtil.FIRSTNAME), netidCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT, CSVFileFormatsUtil.NET), cuidCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT, CSVFileFormatsUtil.CU), collegeCol =
          CSVFileFormatsUtil
              .getColumnNumber(CSVFileFormatsUtil.FINALGRADES_FORMAT,
                  CSVFileFormatsUtil.COLLEGE), deptCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT,
              CSVFileFormatsUtil.DEPARTMENT), courseNumCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT,
              CSVFileFormatsUtil.COURSEID_NUM), courseCodeCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT,
              CSVFileFormatsUtil.COURSEID_CODE), lecCol =
          CSVFileFormatsUtil
              .getColumnNumber(CSVFileFormatsUtil.FINALGRADES_FORMAT,
                  CSVFileFormatsUtil.LECTURE), secCol =
          CSVFileFormatsUtil
              .getColumnNumber(CSVFileFormatsUtil.FINALGRADES_FORMAT,
                  CSVFileFormatsUtil.SECTION), labCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT, CSVFileFormatsUtil.LAB), creditsCol =
          CSVFileFormatsUtil
              .getColumnNumber(CSVFileFormatsUtil.FINALGRADES_FORMAT,
                  CSVFileFormatsUtil.CREDITS), gradeOptCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT,
              CSVFileFormatsUtil.GRADE_OPTION), finalGradeCol =
          CSVFileFormatsUtil.getColumnNumber(
              CSVFileFormatsUtil.FINALGRADES_FORMAT,
              CSVFileFormatsUtil.FINAL_GRADE);
      String[] firstRow = CSVFileFormatsUtil.FINALGRADES_FORMAT;
      output.add(firstRow); // file header row
      // add the data that comes from the user table
      while (users.hasNext()) {
        User user = (User) users.next();
        String[] mydata = new String[numCols];
        output.add(mydata);
        nets.put(user.getNet(), mydata);
        if (courseCodeCol != -1) mydata[courseCodeCol] = course.getCode();
        if (lastnameCol != -1) mydata[lastnameCol] = user.getLastName();
        if (firstnameCol != -1) mydata[firstnameCol] = user.getFirstName();
        if (netidCol != -1) mydata[netidCol] = user.getNet();
        if (cuidCol != -1)
          mydata[cuidCol] = user.getCU() == null ? "" : user.getCU();
        if (collegeCol != -1) mydata[collegeCol] = user.getCollege();
      }
      // add the data that comes from the student table
      while (students.hasNext()) {
        Student student = (Student) students.next();
        String[] mydata = (String[]) nets.get(student.getNet());
        if (mydata != null) {
          if (deptCol != -1)
            mydata[deptCol] =
                student.getDepartment() == null ? "" : student.getDepartment();
          if (courseNumCol != -1)
            mydata[courseNumCol] =
                student.getCourseNum() == null ? "" : student.getCourseNum();
          if (lecCol != -1)
            mydata[lecCol] =
                student.getLecture() == null ? "" : student.getLecture();
          if (labCol != -1)
            mydata[labCol] = student.getLab() == null ? "" : student.getLab();
          if (secCol != -1)
            mydata[secCol] =
                student.getSection() == null ? "" : student.getSection();
          if (creditsCol != -1)
            mydata[creditsCol] =
                student.getCredits() == null ? "" : student.getCredits();
          if (gradeOptCol != -1)
            mydata[gradeOptCol] =
                student.getGradeOption() == null ? "" : student
                    .getGradeOption();
          if (finalGradeCol != -1)
            mydata[finalGradeCol] =
                student.getFinalGrade() == null ? "" : student.getFinalGrade();
        }
      }
      for (int i = 0; i < output.size(); i++) {
        String[] thisRow = (String[]) output.get(i);
        out.println(thisRow);
      }
    } catch (Exception e) {
      e.printStackTrace();
      result.addError("Unexpected error while trying to export final grades");
    }
    return result;
  }

  /**
   * Output the most recently submitted files for the given group to the given
   * ZipOutputStream
   * 
   * @param p
   * @param assignment
   * @param s
   * @return TransactionResult
   */
  public TransactionResult uploadGroupSubmission(Group group,
      ZipOutputStream out, Map submissionNames) {
    TransactionResult result = new TransactionResult();
    try {
      Collection members =
          database.groupMemberHome().findActiveByGroup(group);
      String folder = "Submissions/";
      if (members.size() > 1) {
        folder += "group_of_";
      }
      Iterator i = members.iterator();
      Assignment assign = 0;
      while (i.hasNext()) {
        GroupMember member = (GroupMember) i.next();
        folder += member.getNet() + (i.hasNext() ? "_" : "");
      }
      folder += "/";
      Iterator files =
          database.submittedFileHome().findByGroup(group).iterator();
      if (!files.hasNext()) {
        // Add empty folder if there are no submitted files
        out.putNextEntry(new ZipEntry(folder));
        out.closeEntry();
      }
      byte[] buff = new byte[1024];
      while (files.hasNext()) {
        SubmittedFile file = (SubmittedFile) files.next();
        String filePath = file.getPath();
        /*
         * next line handles missing DB_SLASH at end of path in database, caused
         * by update on 2/28/06 -- Evan
         */
        if (!filePath.endsWith("" + FileUtil.SYS_SLASH))
          filePath += FileUtil.SYS_SLASH;
        filePath += file.appendFileType(String.valueOf(file.getSubmission()));
        FileInputStream in = new FileInputStream(filePath);
        String fileName =
            (String) submissionNames.get(new Long(file.getSubmission()));
        if (fileName == null)
          throw new RemoteException("Submission name map was incomplete.");
        fileName = file.appendFileType(fileName);
        out.putNextEntry(new ZipEntry(folder + fileName));
        int len, sum = 0;
        while ((len = in.read(buff)) > 0) {
          sum += len;
          out.write(buff, 0, len);
        }
        out.closeEntry();
        in.close();
      }
      // out.close();
    } catch (Exception e) {
      result.addError(e.getMessage());
    }
    return result;
  }

  /**
   * Output a zip file containing all the most recently submitted files for all
   * groups given in the groups Collection to the OutputStream
   * 
   * @param groups
   * @param s
   * @return TransactionResult
   */
  public TransactionResult uploadGroupSubmissions(Collection groups,
      OutputStream s) {
    TransactionResult result = new TransactionResult();
    Profiler.enterMethod("TransactionHandler.uploadGroupSubmissions",
        "Uploading files for " + groups.size() + " groups");
    try {
      ZipOutputStream out = new ZipOutputStream(s);
      Iterator i = groups.iterator();
      Map submissionNames = null;
      while (i.hasNext()) {
        Group group = (Group) i.next();
        if (submissionNames == null) {
          submissionNames = database.getSubmissionNameMap(group.getAssignment());
        }
        uploadGroupSubmission(group.longValue(), out, submissionNames);
      }
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
      result.addError(e.getMessage());
    }
    Profiler.exitMethod("TransactionHandler.uploadGroupSubmissions",
        "Uploading files for " + groups.size() + " groups");
    return result;
  }
}
