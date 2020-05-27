import { User, UserWebResponse } from '../../type/user';
import { AppSettings } from '../../app-setting';
import { Subject } from 'rxjs/Subject';
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';

/**
 * User Account Service contains the function of registering and logging the user.
 * It will save the user account inside for future use.
 *
 * @author Adam
 */
@Injectable({
  providedIn: 'root'
})
export class UserService {

  public static readonly AUTH_STATUS_ENDPINT = 'users/auth/status';
  public static readonly LOGIN_ENDPOINT = 'users/login';
  public static readonly REGISTER_ENDPOINT = 'users/register';
  public static readonly LOG_OUT_ENDPOINT = 'users/logout';

  private userChangedSubject: Subject<User | undefined> = new Subject();
  private currentUser: User | undefined;

  constructor(private http: HttpClient) {
    this.loginFromSession();
  }

  /**
   * This method will handle the request for user registration.
   * It will automatically login, save the user account inside and trigger userChangeEvent when success
   * @param userName
   */
  public register(userName: string): Observable<UserWebResponse> {
    // assume the text passed in should be correct
    if (this.currentUser) {throw new Error('Already logged in when register.'); }
    const validation = this.validateUsername(userName);
    if (! validation.result) {
      return Observable.of({
        code: 1,
        message: validation.message
      });
    }

    return this.registerHttpRequest(userName).map(
      res => {
        if (res.code === 0) {
          this.changeUser(res.user);
          return res;
        } else { // register failed
          return res;
        }
      }
    );
  }

  /**
   * This method will handle the request for user login.
   * It will automatically login, save the user account inside and trigger userChangeEvent when success
   * @param userName
   */
  public login(userName: string):  Observable<UserWebResponse> {
    if (this.currentUser) {throw new Error('Already logged in when login in.'); }
    const validation = this.validateUsername(userName);
    if (! validation.result) {
      return Observable.of({
        code: 1,
        message: validation.message
      });
    }

    return this.loginHttpRequest(userName).map(
      res => {
        if (res.code === 0) {
          this.changeUser(res.user);
          return res;
        } else { // login failed
          return res;
        }
      }
    );
  }

  /**
   * this method will clear the saved user account and trigger userChangeEvent
   */
  public logOut(): void {
    this.logOutHttpRequest().subscribe(() => this.changeUser(undefined));
  }

  public getUser(): User | undefined {
    return this.currentUser;
  }

  public isLogin(): boolean {
    return this.currentUser !== undefined;
  }

  /**
   * this method will return the userChangeEvent, which can be subscribe
   * userChangeEvent will be triggered when the current user changes (login or log out)
   */
  public getUserChangedEvent(): Observable<User | undefined> {
    return this.userChangedSubject.asObservable();
  }

  private loginFromSession(): void {
    this.http.get<UserWebResponse>(`${AppSettings.getApiEndpoint()}/${UserService.AUTH_STATUS_ENDPINT}`).subscribe(res => {
      if (res.code === 0) {
        this.changeUser(res.user);
      }
    });
  }

  /**
   * construct the request body as formData and create http request
   * @param userName
   */
  private registerHttpRequest(userName: string): Observable<UserWebResponse> {
    type UserRegistrationRequest = {userName: string};
    const body: UserRegistrationRequest = {userName: userName};
    return this.http.post<UserWebResponse>(`${AppSettings.getApiEndpoint()}/${UserService.REGISTER_ENDPOINT}`, body);
  }

  /**
   * construct the request body as formData and create http request
   * @param userName
   */
  private loginHttpRequest(userName: string): Observable<UserWebResponse> {
    type UserLoginRequest = {userName: string};
    const body: UserLoginRequest = {userName: userName};
    return this.http.post<UserWebResponse>(`${AppSettings.getApiEndpoint()}/${UserService.LOGIN_ENDPOINT}`, body);
  }

    /**
   * construct the request body as formData and create http request
   * @param userName
   */
  private logOutHttpRequest(): Observable<UserWebResponse> {
    return this.http.get<UserWebResponse>(`${AppSettings.getApiEndpoint()}/${UserService.LOG_OUT_ENDPOINT}`);
  }

  /**
   * changes the current user and triggers currentUserSubject
   * @param user
   */
  private changeUser(user: User | undefined): void {
    if (this.currentUser !== user) {
      this.currentUser = user;
      this.userChangedSubject.next(this.currentUser);
    }
  }

  /**
   * check the given parameter is legal for login/registration
   * @param userName
   */
  private validateUsername(userName: string): {result: boolean, message: string} {
    if (userName.trim().length === 0) {
      return { result: false, message: 'userName should not be empty'};
    }
    return { result: true, message: 'userName frontend validation success' };
  }

}
