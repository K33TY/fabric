package cms.model;

public class LogDetail {

  //////////////////////////////////////////////////////////////////////////////
  // private members                                                          //
  //////////////////////////////////////////////////////////////////////////////

  private Log        log;
  private String     detail;
  private Assignment assignment;
  private User       affectedUser;

  //////////////////////////////////////////////////////////////////////////////
  // public setters                                                           //
  //////////////////////////////////////////////////////////////////////////////

  public void setLog        (final Log log)               { this.log        = log;        }
  public void setDetail     (final String detail)         { this.detail     = detail;     }
  public void setAssignment (final Assignment assignment) { this.assignment = assignment; }
  public void setAffectedUser       (final User user)             { this.affectedUser       = user;       }

  //////////////////////////////////////////////////////////////////////////////
  // public getters                                                           //
  //////////////////////////////////////////////////////////////////////////////

  public Log        getLog()        { return this.log;        }
  public String     getDetail()     { return this.detail;     }
  public Assignment getAssignment() { return this.assignment; }
  public User       getAffectedUser()       { return this.affectedUser;       }

  //////////////////////////////////////////////////////////////////////////////
  // public constructors                                                      //
  //////////////////////////////////////////////////////////////////////////////

  public LogDetail (Log log) {
    this(log, null, null, null);
  }

  public LogDetail (Log log, String detail) {
    this(log, detail, null, null);
  }

  public LogDetail (Log log, String detail, User user, Assignment assign) {
    setLog(log);
    setDetail(detail);
    setAffectedUser(user);
    setAssignment(assign);
    
    log.detailLogs.add(this);
  }
}

/*
** vim: ts=2 sw=2 et cindent cino=\:0 syntax=java
*/
