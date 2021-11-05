import { Injectable } from "@angular/core";
import { Observable, ReplaySubject } from "rxjs";
import { User } from "../../type/user";
import { AuthService } from "./auth.service";
import { environment } from "../../../../environments/environment";
import { map, mergeMap } from "rxjs/operators";

/**
 * User Service manages User information. It relies on different
 * auth services to authenticate a valid User.
 */
@Injectable({
  providedIn: "root",
})
export class UserService {
  private currentUser?: User = undefined;
  private userChangeSubject: ReplaySubject<User | undefined> = new ReplaySubject<User | undefined>(1);

  constructor(private authService: AuthService) {
    if (environment.userSystemEnabled) {
      this.authService.loginWithExistingToken().subscribe(user => this.changeUser(user));
    }
  }

  public login(username: string, password: string): Observable<void> {
    // validate the credentials with backend
    return this.authService
      .auth(username, password)
      .pipe(mergeMap(({ accessToken }) => this.handleAccessToken(accessToken)));
  }

  public googleLogin(): Observable<void> {
    return this.authService.googleAuth().pipe(mergeMap(({ accessToken }) => this.handleAccessToken(accessToken)));
  }

  public isLogin(): boolean {
    return this.currentUser !== undefined;
  }

  public userChanged(): Observable<User | undefined> {
    return this.userChangeSubject.asObservable();
  }

  public logout(): void {
    this.authService.logout().subscribe(_ => this.changeUser(undefined));
  }

  public register(username: string, password: string): Observable<void> {
    return this.authService
      .register(username, password)
      .pipe(mergeMap(({ accessToken }) => this.handleAccessToken(accessToken)));
  }

  /**
   * changes the current user and triggers currentUserSubject
   * @param user
   */
  private changeUser(user: User | undefined): void {
    this.currentUser = user;
    this.userChangeSubject.next(this.currentUser);
  }

  private handleAccessToken(accessToken: string): Observable<void> {
    AuthService.setAccessToken(accessToken);
    return this.authService.loginWithExistingToken().pipe(map(user => this.changeUser(user)));
  }

  /**
   * check the given parameter is legal for login/registration
   * @param username
   */
  static validateUsername(username: string): { result: boolean; message: string } {
    if (username.trim().length === 0) {
      return { result: false, message: "Username should not be empty." };
    }
    return { result: true, message: "Username frontend validation success." };
  }
}
