package com.textpic.studio.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.paging.LimitOffsetPagingSource;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MessageDao_Impl implements MessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MessageEntity> __insertionAdapterOfMessageEntity;

  private final EntityDeletionOrUpdateAdapter<MessageEntity> __deletionAdapterOfMessageEntity;

  private final EntityDeletionOrUpdateAdapter<MessageEntity> __updateAdapterOfMessageEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public MessageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMessageEntity = new EntityInsertionAdapter<MessageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `messages` (`id`,`createdAt`,`text`,`styleKey`,`theme`,`font`,`fontSize`,`maxWidth`,`alignment`,`fillWidth`,`transparent`,`scale`,`paddingX`,`paddingY`,`thumbnailFit`,`widthPx`,`heightPx`,`chars`,`status`,`insertedAt`,`pinned`,`imagePath`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MessageEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getCreatedAt());
        if (entity.getText() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getText());
        }
        statement.bindString(4, entity.getStyleKey());
        statement.bindString(5, entity.getTheme());
        statement.bindString(6, entity.getFont());
        statement.bindLong(7, entity.getFontSize());
        statement.bindLong(8, entity.getMaxWidth());
        statement.bindString(9, entity.getAlignment());
        final int _tmp = entity.getFillWidth() ? 1 : 0;
        statement.bindLong(10, _tmp);
        final int _tmp_1 = entity.getTransparent() ? 1 : 0;
        statement.bindLong(11, _tmp_1);
        statement.bindDouble(12, entity.getScale());
        statement.bindLong(13, entity.getPaddingX());
        statement.bindLong(14, entity.getPaddingY());
        statement.bindString(15, entity.getThumbnailFit());
        statement.bindLong(16, entity.getWidthPx());
        statement.bindLong(17, entity.getHeightPx());
        statement.bindLong(18, entity.getChars());
        statement.bindString(19, entity.getStatus());
        if (entity.getInsertedAt() == null) {
          statement.bindNull(20);
        } else {
          statement.bindLong(20, entity.getInsertedAt());
        }
        final int _tmp_2 = entity.getPinned() ? 1 : 0;
        statement.bindLong(21, _tmp_2);
        if (entity.getImagePath() == null) {
          statement.bindNull(22);
        } else {
          statement.bindString(22, entity.getImagePath());
        }
      }
    };
    this.__deletionAdapterOfMessageEntity = new EntityDeletionOrUpdateAdapter<MessageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `messages` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MessageEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfMessageEntity = new EntityDeletionOrUpdateAdapter<MessageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `messages` SET `id` = ?,`createdAt` = ?,`text` = ?,`styleKey` = ?,`theme` = ?,`font` = ?,`fontSize` = ?,`maxWidth` = ?,`alignment` = ?,`fillWidth` = ?,`transparent` = ?,`scale` = ?,`paddingX` = ?,`paddingY` = ?,`thumbnailFit` = ?,`widthPx` = ?,`heightPx` = ?,`chars` = ?,`status` = ?,`insertedAt` = ?,`pinned` = ?,`imagePath` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MessageEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getCreatedAt());
        if (entity.getText() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getText());
        }
        statement.bindString(4, entity.getStyleKey());
        statement.bindString(5, entity.getTheme());
        statement.bindString(6, entity.getFont());
        statement.bindLong(7, entity.getFontSize());
        statement.bindLong(8, entity.getMaxWidth());
        statement.bindString(9, entity.getAlignment());
        final int _tmp = entity.getFillWidth() ? 1 : 0;
        statement.bindLong(10, _tmp);
        final int _tmp_1 = entity.getTransparent() ? 1 : 0;
        statement.bindLong(11, _tmp_1);
        statement.bindDouble(12, entity.getScale());
        statement.bindLong(13, entity.getPaddingX());
        statement.bindLong(14, entity.getPaddingY());
        statement.bindString(15, entity.getThumbnailFit());
        statement.bindLong(16, entity.getWidthPx());
        statement.bindLong(17, entity.getHeightPx());
        statement.bindLong(18, entity.getChars());
        statement.bindString(19, entity.getStatus());
        if (entity.getInsertedAt() == null) {
          statement.bindNull(20);
        } else {
          statement.bindLong(20, entity.getInsertedAt());
        }
        final int _tmp_2 = entity.getPinned() ? 1 : 0;
        statement.bindLong(21, _tmp_2);
        if (entity.getImagePath() == null) {
          statement.bindNull(22);
        } else {
          statement.bindString(22, entity.getImagePath());
        }
        statement.bindLong(23, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages WHERE createdAt < ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final MessageEntity message, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfMessageEntity.insertAndReturnId(message);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final MessageEntity message, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfMessageEntity.handle(message);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final MessageEntity message, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfMessageEntity.handle(message);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOlderThan(final long cutoff, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOlderThan.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, cutoff);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteOlderThan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public PagingSource<Integer, MessageEntity> pagingSource(final String query,
      final String status) {
    final String _sql = "\n"
            + "        SELECT * FROM messages\n"
            + "        WHERE (? = '' OR (text IS NOT NULL AND text LIKE '%' || ? || '%'))\n"
            + "        AND (? = '' OR status = ?)\n"
            + "        ORDER BY pinned DESC, createdAt DESC\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindString(_argIndex, query);
    _argIndex = 2;
    _statement.bindString(_argIndex, query);
    _argIndex = 3;
    _statement.bindString(_argIndex, status);
    _argIndex = 4;
    _statement.bindString(_argIndex, status);
    return new LimitOffsetPagingSource<MessageEntity>(_statement, __db, "messages") {
      @Override
      @NonNull
      protected List<MessageEntity> convertRows(@NonNull final Cursor cursor) {
        final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(cursor, "id");
        final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(cursor, "createdAt");
        final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(cursor, "text");
        final int _cursorIndexOfStyleKey = CursorUtil.getColumnIndexOrThrow(cursor, "styleKey");
        final int _cursorIndexOfTheme = CursorUtil.getColumnIndexOrThrow(cursor, "theme");
        final int _cursorIndexOfFont = CursorUtil.getColumnIndexOrThrow(cursor, "font");
        final int _cursorIndexOfFontSize = CursorUtil.getColumnIndexOrThrow(cursor, "fontSize");
        final int _cursorIndexOfMaxWidth = CursorUtil.getColumnIndexOrThrow(cursor, "maxWidth");
        final int _cursorIndexOfAlignment = CursorUtil.getColumnIndexOrThrow(cursor, "alignment");
        final int _cursorIndexOfFillWidth = CursorUtil.getColumnIndexOrThrow(cursor, "fillWidth");
        final int _cursorIndexOfTransparent = CursorUtil.getColumnIndexOrThrow(cursor, "transparent");
        final int _cursorIndexOfScale = CursorUtil.getColumnIndexOrThrow(cursor, "scale");
        final int _cursorIndexOfPaddingX = CursorUtil.getColumnIndexOrThrow(cursor, "paddingX");
        final int _cursorIndexOfPaddingY = CursorUtil.getColumnIndexOrThrow(cursor, "paddingY");
        final int _cursorIndexOfThumbnailFit = CursorUtil.getColumnIndexOrThrow(cursor, "thumbnailFit");
        final int _cursorIndexOfWidthPx = CursorUtil.getColumnIndexOrThrow(cursor, "widthPx");
        final int _cursorIndexOfHeightPx = CursorUtil.getColumnIndexOrThrow(cursor, "heightPx");
        final int _cursorIndexOfChars = CursorUtil.getColumnIndexOrThrow(cursor, "chars");
        final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(cursor, "status");
        final int _cursorIndexOfInsertedAt = CursorUtil.getColumnIndexOrThrow(cursor, "insertedAt");
        final int _cursorIndexOfPinned = CursorUtil.getColumnIndexOrThrow(cursor, "pinned");
        final int _cursorIndexOfImagePath = CursorUtil.getColumnIndexOrThrow(cursor, "imagePath");
        final List<MessageEntity> _result = new ArrayList<MessageEntity>(cursor.getCount());
        while (cursor.moveToNext()) {
          final MessageEntity _item;
          final long _tmpId;
          _tmpId = cursor.getLong(_cursorIndexOfId);
          final long _tmpCreatedAt;
          _tmpCreatedAt = cursor.getLong(_cursorIndexOfCreatedAt);
          final String _tmpText;
          if (cursor.isNull(_cursorIndexOfText)) {
            _tmpText = null;
          } else {
            _tmpText = cursor.getString(_cursorIndexOfText);
          }
          final String _tmpStyleKey;
          _tmpStyleKey = cursor.getString(_cursorIndexOfStyleKey);
          final String _tmpTheme;
          _tmpTheme = cursor.getString(_cursorIndexOfTheme);
          final String _tmpFont;
          _tmpFont = cursor.getString(_cursorIndexOfFont);
          final int _tmpFontSize;
          _tmpFontSize = cursor.getInt(_cursorIndexOfFontSize);
          final int _tmpMaxWidth;
          _tmpMaxWidth = cursor.getInt(_cursorIndexOfMaxWidth);
          final String _tmpAlignment;
          _tmpAlignment = cursor.getString(_cursorIndexOfAlignment);
          final boolean _tmpFillWidth;
          final int _tmp;
          _tmp = cursor.getInt(_cursorIndexOfFillWidth);
          _tmpFillWidth = _tmp != 0;
          final boolean _tmpTransparent;
          final int _tmp_1;
          _tmp_1 = cursor.getInt(_cursorIndexOfTransparent);
          _tmpTransparent = _tmp_1 != 0;
          final float _tmpScale;
          _tmpScale = cursor.getFloat(_cursorIndexOfScale);
          final int _tmpPaddingX;
          _tmpPaddingX = cursor.getInt(_cursorIndexOfPaddingX);
          final int _tmpPaddingY;
          _tmpPaddingY = cursor.getInt(_cursorIndexOfPaddingY);
          final String _tmpThumbnailFit;
          _tmpThumbnailFit = cursor.getString(_cursorIndexOfThumbnailFit);
          final int _tmpWidthPx;
          _tmpWidthPx = cursor.getInt(_cursorIndexOfWidthPx);
          final int _tmpHeightPx;
          _tmpHeightPx = cursor.getInt(_cursorIndexOfHeightPx);
          final int _tmpChars;
          _tmpChars = cursor.getInt(_cursorIndexOfChars);
          final String _tmpStatus;
          _tmpStatus = cursor.getString(_cursorIndexOfStatus);
          final Long _tmpInsertedAt;
          if (cursor.isNull(_cursorIndexOfInsertedAt)) {
            _tmpInsertedAt = null;
          } else {
            _tmpInsertedAt = cursor.getLong(_cursorIndexOfInsertedAt);
          }
          final boolean _tmpPinned;
          final int _tmp_2;
          _tmp_2 = cursor.getInt(_cursorIndexOfPinned);
          _tmpPinned = _tmp_2 != 0;
          final String _tmpImagePath;
          if (cursor.isNull(_cursorIndexOfImagePath)) {
            _tmpImagePath = null;
          } else {
            _tmpImagePath = cursor.getString(_cursorIndexOfImagePath);
          }
          _item = new MessageEntity(_tmpId,_tmpCreatedAt,_tmpText,_tmpStyleKey,_tmpTheme,_tmpFont,_tmpFontSize,_tmpMaxWidth,_tmpAlignment,_tmpFillWidth,_tmpTransparent,_tmpScale,_tmpPaddingX,_tmpPaddingY,_tmpThumbnailFit,_tmpWidthPx,_tmpHeightPx,_tmpChars,_tmpStatus,_tmpInsertedAt,_tmpPinned,_tmpImagePath);
          _result.add(_item);
        }
        return _result;
      }
    };
  }

  @Override
  public Object getAll(final Continuation<? super List<MessageEntity>> $completion) {
    final String _sql = "SELECT * FROM messages ORDER BY pinned DESC, createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfStyleKey = CursorUtil.getColumnIndexOrThrow(_cursor, "styleKey");
          final int _cursorIndexOfTheme = CursorUtil.getColumnIndexOrThrow(_cursor, "theme");
          final int _cursorIndexOfFont = CursorUtil.getColumnIndexOrThrow(_cursor, "font");
          final int _cursorIndexOfFontSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fontSize");
          final int _cursorIndexOfMaxWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "maxWidth");
          final int _cursorIndexOfAlignment = CursorUtil.getColumnIndexOrThrow(_cursor, "alignment");
          final int _cursorIndexOfFillWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "fillWidth");
          final int _cursorIndexOfTransparent = CursorUtil.getColumnIndexOrThrow(_cursor, "transparent");
          final int _cursorIndexOfScale = CursorUtil.getColumnIndexOrThrow(_cursor, "scale");
          final int _cursorIndexOfPaddingX = CursorUtil.getColumnIndexOrThrow(_cursor, "paddingX");
          final int _cursorIndexOfPaddingY = CursorUtil.getColumnIndexOrThrow(_cursor, "paddingY");
          final int _cursorIndexOfThumbnailFit = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailFit");
          final int _cursorIndexOfWidthPx = CursorUtil.getColumnIndexOrThrow(_cursor, "widthPx");
          final int _cursorIndexOfHeightPx = CursorUtil.getColumnIndexOrThrow(_cursor, "heightPx");
          final int _cursorIndexOfChars = CursorUtil.getColumnIndexOrThrow(_cursor, "chars");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfInsertedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "insertedAt");
          final int _cursorIndexOfPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "pinned");
          final int _cursorIndexOfImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "imagePath");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpText;
            if (_cursor.isNull(_cursorIndexOfText)) {
              _tmpText = null;
            } else {
              _tmpText = _cursor.getString(_cursorIndexOfText);
            }
            final String _tmpStyleKey;
            _tmpStyleKey = _cursor.getString(_cursorIndexOfStyleKey);
            final String _tmpTheme;
            _tmpTheme = _cursor.getString(_cursorIndexOfTheme);
            final String _tmpFont;
            _tmpFont = _cursor.getString(_cursorIndexOfFont);
            final int _tmpFontSize;
            _tmpFontSize = _cursor.getInt(_cursorIndexOfFontSize);
            final int _tmpMaxWidth;
            _tmpMaxWidth = _cursor.getInt(_cursorIndexOfMaxWidth);
            final String _tmpAlignment;
            _tmpAlignment = _cursor.getString(_cursorIndexOfAlignment);
            final boolean _tmpFillWidth;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFillWidth);
            _tmpFillWidth = _tmp != 0;
            final boolean _tmpTransparent;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfTransparent);
            _tmpTransparent = _tmp_1 != 0;
            final float _tmpScale;
            _tmpScale = _cursor.getFloat(_cursorIndexOfScale);
            final int _tmpPaddingX;
            _tmpPaddingX = _cursor.getInt(_cursorIndexOfPaddingX);
            final int _tmpPaddingY;
            _tmpPaddingY = _cursor.getInt(_cursorIndexOfPaddingY);
            final String _tmpThumbnailFit;
            _tmpThumbnailFit = _cursor.getString(_cursorIndexOfThumbnailFit);
            final int _tmpWidthPx;
            _tmpWidthPx = _cursor.getInt(_cursorIndexOfWidthPx);
            final int _tmpHeightPx;
            _tmpHeightPx = _cursor.getInt(_cursorIndexOfHeightPx);
            final int _tmpChars;
            _tmpChars = _cursor.getInt(_cursorIndexOfChars);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final Long _tmpInsertedAt;
            if (_cursor.isNull(_cursorIndexOfInsertedAt)) {
              _tmpInsertedAt = null;
            } else {
              _tmpInsertedAt = _cursor.getLong(_cursorIndexOfInsertedAt);
            }
            final boolean _tmpPinned;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfPinned);
            _tmpPinned = _tmp_2 != 0;
            final String _tmpImagePath;
            if (_cursor.isNull(_cursorIndexOfImagePath)) {
              _tmpImagePath = null;
            } else {
              _tmpImagePath = _cursor.getString(_cursorIndexOfImagePath);
            }
            _item = new MessageEntity(_tmpId,_tmpCreatedAt,_tmpText,_tmpStyleKey,_tmpTheme,_tmpFont,_tmpFontSize,_tmpMaxWidth,_tmpAlignment,_tmpFillWidth,_tmpTransparent,_tmpScale,_tmpPaddingX,_tmpPaddingY,_tmpThumbnailFit,_tmpWidthPx,_tmpHeightPx,_tmpChars,_tmpStatus,_tmpInsertedAt,_tmpPinned,_tmpImagePath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getOlderThan(final long cutoff,
      final Continuation<? super List<MessageEntity>> $completion) {
    final String _sql = "SELECT * FROM messages WHERE createdAt < ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, cutoff);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfStyleKey = CursorUtil.getColumnIndexOrThrow(_cursor, "styleKey");
          final int _cursorIndexOfTheme = CursorUtil.getColumnIndexOrThrow(_cursor, "theme");
          final int _cursorIndexOfFont = CursorUtil.getColumnIndexOrThrow(_cursor, "font");
          final int _cursorIndexOfFontSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fontSize");
          final int _cursorIndexOfMaxWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "maxWidth");
          final int _cursorIndexOfAlignment = CursorUtil.getColumnIndexOrThrow(_cursor, "alignment");
          final int _cursorIndexOfFillWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "fillWidth");
          final int _cursorIndexOfTransparent = CursorUtil.getColumnIndexOrThrow(_cursor, "transparent");
          final int _cursorIndexOfScale = CursorUtil.getColumnIndexOrThrow(_cursor, "scale");
          final int _cursorIndexOfPaddingX = CursorUtil.getColumnIndexOrThrow(_cursor, "paddingX");
          final int _cursorIndexOfPaddingY = CursorUtil.getColumnIndexOrThrow(_cursor, "paddingY");
          final int _cursorIndexOfThumbnailFit = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailFit");
          final int _cursorIndexOfWidthPx = CursorUtil.getColumnIndexOrThrow(_cursor, "widthPx");
          final int _cursorIndexOfHeightPx = CursorUtil.getColumnIndexOrThrow(_cursor, "heightPx");
          final int _cursorIndexOfChars = CursorUtil.getColumnIndexOrThrow(_cursor, "chars");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfInsertedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "insertedAt");
          final int _cursorIndexOfPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "pinned");
          final int _cursorIndexOfImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "imagePath");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpText;
            if (_cursor.isNull(_cursorIndexOfText)) {
              _tmpText = null;
            } else {
              _tmpText = _cursor.getString(_cursorIndexOfText);
            }
            final String _tmpStyleKey;
            _tmpStyleKey = _cursor.getString(_cursorIndexOfStyleKey);
            final String _tmpTheme;
            _tmpTheme = _cursor.getString(_cursorIndexOfTheme);
            final String _tmpFont;
            _tmpFont = _cursor.getString(_cursorIndexOfFont);
            final int _tmpFontSize;
            _tmpFontSize = _cursor.getInt(_cursorIndexOfFontSize);
            final int _tmpMaxWidth;
            _tmpMaxWidth = _cursor.getInt(_cursorIndexOfMaxWidth);
            final String _tmpAlignment;
            _tmpAlignment = _cursor.getString(_cursorIndexOfAlignment);
            final boolean _tmpFillWidth;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFillWidth);
            _tmpFillWidth = _tmp != 0;
            final boolean _tmpTransparent;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfTransparent);
            _tmpTransparent = _tmp_1 != 0;
            final float _tmpScale;
            _tmpScale = _cursor.getFloat(_cursorIndexOfScale);
            final int _tmpPaddingX;
            _tmpPaddingX = _cursor.getInt(_cursorIndexOfPaddingX);
            final int _tmpPaddingY;
            _tmpPaddingY = _cursor.getInt(_cursorIndexOfPaddingY);
            final String _tmpThumbnailFit;
            _tmpThumbnailFit = _cursor.getString(_cursorIndexOfThumbnailFit);
            final int _tmpWidthPx;
            _tmpWidthPx = _cursor.getInt(_cursorIndexOfWidthPx);
            final int _tmpHeightPx;
            _tmpHeightPx = _cursor.getInt(_cursorIndexOfHeightPx);
            final int _tmpChars;
            _tmpChars = _cursor.getInt(_cursorIndexOfChars);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final Long _tmpInsertedAt;
            if (_cursor.isNull(_cursorIndexOfInsertedAt)) {
              _tmpInsertedAt = null;
            } else {
              _tmpInsertedAt = _cursor.getLong(_cursorIndexOfInsertedAt);
            }
            final boolean _tmpPinned;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfPinned);
            _tmpPinned = _tmp_2 != 0;
            final String _tmpImagePath;
            if (_cursor.isNull(_cursorIndexOfImagePath)) {
              _tmpImagePath = null;
            } else {
              _tmpImagePath = _cursor.getString(_cursorIndexOfImagePath);
            }
            _item = new MessageEntity(_tmpId,_tmpCreatedAt,_tmpText,_tmpStyleKey,_tmpTheme,_tmpFont,_tmpFontSize,_tmpMaxWidth,_tmpAlignment,_tmpFillWidth,_tmpTransparent,_tmpScale,_tmpPaddingX,_tmpPaddingY,_tmpThumbnailFit,_tmpWidthPx,_tmpHeightPx,_tmpChars,_tmpStatus,_tmpInsertedAt,_tmpPinned,_tmpImagePath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<MessageEntity>> observeRecent(final int limit) {
    final String _sql = "SELECT * FROM messages ORDER BY pinned DESC, createdAt DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfStyleKey = CursorUtil.getColumnIndexOrThrow(_cursor, "styleKey");
          final int _cursorIndexOfTheme = CursorUtil.getColumnIndexOrThrow(_cursor, "theme");
          final int _cursorIndexOfFont = CursorUtil.getColumnIndexOrThrow(_cursor, "font");
          final int _cursorIndexOfFontSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fontSize");
          final int _cursorIndexOfMaxWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "maxWidth");
          final int _cursorIndexOfAlignment = CursorUtil.getColumnIndexOrThrow(_cursor, "alignment");
          final int _cursorIndexOfFillWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "fillWidth");
          final int _cursorIndexOfTransparent = CursorUtil.getColumnIndexOrThrow(_cursor, "transparent");
          final int _cursorIndexOfScale = CursorUtil.getColumnIndexOrThrow(_cursor, "scale");
          final int _cursorIndexOfPaddingX = CursorUtil.getColumnIndexOrThrow(_cursor, "paddingX");
          final int _cursorIndexOfPaddingY = CursorUtil.getColumnIndexOrThrow(_cursor, "paddingY");
          final int _cursorIndexOfThumbnailFit = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailFit");
          final int _cursorIndexOfWidthPx = CursorUtil.getColumnIndexOrThrow(_cursor, "widthPx");
          final int _cursorIndexOfHeightPx = CursorUtil.getColumnIndexOrThrow(_cursor, "heightPx");
          final int _cursorIndexOfChars = CursorUtil.getColumnIndexOrThrow(_cursor, "chars");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfInsertedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "insertedAt");
          final int _cursorIndexOfPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "pinned");
          final int _cursorIndexOfImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "imagePath");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpText;
            if (_cursor.isNull(_cursorIndexOfText)) {
              _tmpText = null;
            } else {
              _tmpText = _cursor.getString(_cursorIndexOfText);
            }
            final String _tmpStyleKey;
            _tmpStyleKey = _cursor.getString(_cursorIndexOfStyleKey);
            final String _tmpTheme;
            _tmpTheme = _cursor.getString(_cursorIndexOfTheme);
            final String _tmpFont;
            _tmpFont = _cursor.getString(_cursorIndexOfFont);
            final int _tmpFontSize;
            _tmpFontSize = _cursor.getInt(_cursorIndexOfFontSize);
            final int _tmpMaxWidth;
            _tmpMaxWidth = _cursor.getInt(_cursorIndexOfMaxWidth);
            final String _tmpAlignment;
            _tmpAlignment = _cursor.getString(_cursorIndexOfAlignment);
            final boolean _tmpFillWidth;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFillWidth);
            _tmpFillWidth = _tmp != 0;
            final boolean _tmpTransparent;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfTransparent);
            _tmpTransparent = _tmp_1 != 0;
            final float _tmpScale;
            _tmpScale = _cursor.getFloat(_cursorIndexOfScale);
            final int _tmpPaddingX;
            _tmpPaddingX = _cursor.getInt(_cursorIndexOfPaddingX);
            final int _tmpPaddingY;
            _tmpPaddingY = _cursor.getInt(_cursorIndexOfPaddingY);
            final String _tmpThumbnailFit;
            _tmpThumbnailFit = _cursor.getString(_cursorIndexOfThumbnailFit);
            final int _tmpWidthPx;
            _tmpWidthPx = _cursor.getInt(_cursorIndexOfWidthPx);
            final int _tmpHeightPx;
            _tmpHeightPx = _cursor.getInt(_cursorIndexOfHeightPx);
            final int _tmpChars;
            _tmpChars = _cursor.getInt(_cursorIndexOfChars);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final Long _tmpInsertedAt;
            if (_cursor.isNull(_cursorIndexOfInsertedAt)) {
              _tmpInsertedAt = null;
            } else {
              _tmpInsertedAt = _cursor.getLong(_cursorIndexOfInsertedAt);
            }
            final boolean _tmpPinned;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfPinned);
            _tmpPinned = _tmp_2 != 0;
            final String _tmpImagePath;
            if (_cursor.isNull(_cursorIndexOfImagePath)) {
              _tmpImagePath = null;
            } else {
              _tmpImagePath = _cursor.getString(_cursorIndexOfImagePath);
            }
            _item = new MessageEntity(_tmpId,_tmpCreatedAt,_tmpText,_tmpStyleKey,_tmpTheme,_tmpFont,_tmpFontSize,_tmpMaxWidth,_tmpAlignment,_tmpFillWidth,_tmpTransparent,_tmpScale,_tmpPaddingX,_tmpPaddingY,_tmpThumbnailFit,_tmpWidthPx,_tmpHeightPx,_tmpChars,_tmpStatus,_tmpInsertedAt,_tmpPinned,_tmpImagePath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> observeTotal() {
    final String _sql = "SELECT COUNT(*) FROM messages";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> observeInsertedShared() {
    final String _sql = "SELECT COUNT(*) FROM messages WHERE status IN ('INSERTED', 'SHARED')";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> observeInsertedSharedSince(final long since) {
    final String _sql = "SELECT COUNT(*) FROM messages WHERE status IN ('INSERTED', 'SHARED') AND createdAt >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> observeCountSince(final long since) {
    final String _sql = "SELECT COUNT(*) FROM messages WHERE createdAt >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final long id, final Continuation<? super MessageEntity> $completion) {
    final String _sql = "SELECT * FROM messages WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MessageEntity>() {
      @Override
      @Nullable
      public MessageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfStyleKey = CursorUtil.getColumnIndexOrThrow(_cursor, "styleKey");
          final int _cursorIndexOfTheme = CursorUtil.getColumnIndexOrThrow(_cursor, "theme");
          final int _cursorIndexOfFont = CursorUtil.getColumnIndexOrThrow(_cursor, "font");
          final int _cursorIndexOfFontSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fontSize");
          final int _cursorIndexOfMaxWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "maxWidth");
          final int _cursorIndexOfAlignment = CursorUtil.getColumnIndexOrThrow(_cursor, "alignment");
          final int _cursorIndexOfFillWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "fillWidth");
          final int _cursorIndexOfTransparent = CursorUtil.getColumnIndexOrThrow(_cursor, "transparent");
          final int _cursorIndexOfScale = CursorUtil.getColumnIndexOrThrow(_cursor, "scale");
          final int _cursorIndexOfPaddingX = CursorUtil.getColumnIndexOrThrow(_cursor, "paddingX");
          final int _cursorIndexOfPaddingY = CursorUtil.getColumnIndexOrThrow(_cursor, "paddingY");
          final int _cursorIndexOfThumbnailFit = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailFit");
          final int _cursorIndexOfWidthPx = CursorUtil.getColumnIndexOrThrow(_cursor, "widthPx");
          final int _cursorIndexOfHeightPx = CursorUtil.getColumnIndexOrThrow(_cursor, "heightPx");
          final int _cursorIndexOfChars = CursorUtil.getColumnIndexOrThrow(_cursor, "chars");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfInsertedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "insertedAt");
          final int _cursorIndexOfPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "pinned");
          final int _cursorIndexOfImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "imagePath");
          final MessageEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpText;
            if (_cursor.isNull(_cursorIndexOfText)) {
              _tmpText = null;
            } else {
              _tmpText = _cursor.getString(_cursorIndexOfText);
            }
            final String _tmpStyleKey;
            _tmpStyleKey = _cursor.getString(_cursorIndexOfStyleKey);
            final String _tmpTheme;
            _tmpTheme = _cursor.getString(_cursorIndexOfTheme);
            final String _tmpFont;
            _tmpFont = _cursor.getString(_cursorIndexOfFont);
            final int _tmpFontSize;
            _tmpFontSize = _cursor.getInt(_cursorIndexOfFontSize);
            final int _tmpMaxWidth;
            _tmpMaxWidth = _cursor.getInt(_cursorIndexOfMaxWidth);
            final String _tmpAlignment;
            _tmpAlignment = _cursor.getString(_cursorIndexOfAlignment);
            final boolean _tmpFillWidth;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFillWidth);
            _tmpFillWidth = _tmp != 0;
            final boolean _tmpTransparent;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfTransparent);
            _tmpTransparent = _tmp_1 != 0;
            final float _tmpScale;
            _tmpScale = _cursor.getFloat(_cursorIndexOfScale);
            final int _tmpPaddingX;
            _tmpPaddingX = _cursor.getInt(_cursorIndexOfPaddingX);
            final int _tmpPaddingY;
            _tmpPaddingY = _cursor.getInt(_cursorIndexOfPaddingY);
            final String _tmpThumbnailFit;
            _tmpThumbnailFit = _cursor.getString(_cursorIndexOfThumbnailFit);
            final int _tmpWidthPx;
            _tmpWidthPx = _cursor.getInt(_cursorIndexOfWidthPx);
            final int _tmpHeightPx;
            _tmpHeightPx = _cursor.getInt(_cursorIndexOfHeightPx);
            final int _tmpChars;
            _tmpChars = _cursor.getInt(_cursorIndexOfChars);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final Long _tmpInsertedAt;
            if (_cursor.isNull(_cursorIndexOfInsertedAt)) {
              _tmpInsertedAt = null;
            } else {
              _tmpInsertedAt = _cursor.getLong(_cursorIndexOfInsertedAt);
            }
            final boolean _tmpPinned;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfPinned);
            _tmpPinned = _tmp_2 != 0;
            final String _tmpImagePath;
            if (_cursor.isNull(_cursorIndexOfImagePath)) {
              _tmpImagePath = null;
            } else {
              _tmpImagePath = _cursor.getString(_cursorIndexOfImagePath);
            }
            _result = new MessageEntity(_tmpId,_tmpCreatedAt,_tmpText,_tmpStyleKey,_tmpTheme,_tmpFont,_tmpFontSize,_tmpMaxWidth,_tmpAlignment,_tmpFillWidth,_tmpTransparent,_tmpScale,_tmpPaddingX,_tmpPaddingY,_tmpThumbnailFit,_tmpWidthPx,_tmpHeightPx,_tmpChars,_tmpStatus,_tmpInsertedAt,_tmpPinned,_tmpImagePath);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Double> observeAverageChars() {
    final String _sql = "SELECT AVG(chars) FROM messages";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Double> observeAverageCharsSince(final long since) {
    final String _sql = "SELECT AVG(chars) FROM messages WHERE createdAt >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<StyleCount>> observeStyleCountsSince(final long since) {
    final String _sql = "SELECT styleKey, COUNT(*) as count FROM messages WHERE createdAt >= ? GROUP BY styleKey ORDER BY count DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<StyleCount>>() {
      @Override
      @NonNull
      public List<StyleCount> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfStyleKey = 0;
          final int _cursorIndexOfCount = 1;
          final List<StyleCount> _result = new ArrayList<StyleCount>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StyleCount _item;
            final String _tmpStyleKey;
            _tmpStyleKey = _cursor.getString(_cursorIndexOfStyleKey);
            final int _tmpCount;
            _tmpCount = _cursor.getInt(_cursorIndexOfCount);
            _item = new StyleCount(_tmpStyleKey,_tmpCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<StyleCount>> observeStyleCounts() {
    final String _sql = "SELECT styleKey, COUNT(*) as count FROM messages GROUP BY styleKey ORDER BY count DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<StyleCount>>() {
      @Override
      @NonNull
      public List<StyleCount> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfStyleKey = 0;
          final int _cursorIndexOfCount = 1;
          final List<StyleCount> _result = new ArrayList<StyleCount>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StyleCount _item;
            final String _tmpStyleKey;
            _tmpStyleKey = _cursor.getString(_cursorIndexOfStyleKey);
            final int _tmpCount;
            _tmpCount = _cursor.getInt(_cursorIndexOfCount);
            _item = new StyleCount(_tmpStyleKey,_tmpCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<DailyCount>> observeDailyCounts(final long since) {
    final String _sql = "\n"
            + "        SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count\n"
            + "        FROM messages WHERE createdAt >= ?\n"
            + "        GROUP BY day ORDER BY day\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<DailyCount>>() {
      @Override
      @NonNull
      public List<DailyCount> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDay = 0;
          final int _cursorIndexOfCount = 1;
          final List<DailyCount> _result = new ArrayList<DailyCount>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DailyCount _item;
            final String _tmpDay;
            _tmpDay = _cursor.getString(_cursorIndexOfDay);
            final int _tmpCount;
            _tmpCount = _cursor.getInt(_cursorIndexOfCount);
            _item = new DailyCount(_tmpDay,_tmpCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<DailyStatusCount>> observeDailyStatusCounts(final long since) {
    final String _sql = "\n"
            + "        SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS day, status, COUNT(*) AS count\n"
            + "        FROM messages WHERE createdAt >= ?\n"
            + "        GROUP BY day, status ORDER BY day\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<DailyStatusCount>>() {
      @Override
      @NonNull
      public List<DailyStatusCount> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDay = 0;
          final int _cursorIndexOfStatus = 1;
          final int _cursorIndexOfCount = 2;
          final List<DailyStatusCount> _result = new ArrayList<DailyStatusCount>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DailyStatusCount _item;
            final String _tmpDay;
            _tmpDay = _cursor.getString(_cursorIndexOfDay);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final int _tmpCount;
            _tmpCount = _cursor.getInt(_cursorIndexOfCount);
            _item = new DailyStatusCount(_tmpDay,_tmpStatus,_tmpCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<HourCount>> observeHourCounts(final long since) {
    final String _sql = "\n"
            + "        SELECT strftime('%H', createdAt / 1000, 'unixepoch', 'localtime') AS hour, COUNT(*) AS count\n"
            + "        FROM messages WHERE createdAt >= ?\n"
            + "        GROUP BY hour ORDER BY hour\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<HourCount>>() {
      @Override
      @NonNull
      public List<HourCount> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfHour = 0;
          final int _cursorIndexOfCount = 1;
          final List<HourCount> _result = new ArrayList<HourCount>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HourCount _item;
            final String _tmpHour;
            _tmpHour = _cursor.getString(_cursorIndexOfHour);
            final int _tmpCount;
            _tmpCount = _cursor.getInt(_cursorIndexOfCount);
            _item = new HourCount(_tmpHour,_tmpCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<DailyAverage>> observeDailyAverageChars(final long since) {
    final String _sql = "\n"
            + "        SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS day, AVG(chars) AS average\n"
            + "        FROM messages WHERE createdAt >= ?\n"
            + "        GROUP BY day ORDER BY day\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<DailyAverage>>() {
      @Override
      @NonNull
      public List<DailyAverage> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDay = 0;
          final int _cursorIndexOfAverage = 1;
          final List<DailyAverage> _result = new ArrayList<DailyAverage>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DailyAverage _item;
            final String _tmpDay;
            _tmpDay = _cursor.getString(_cursorIndexOfDay);
            final double _tmpAverage;
            _tmpAverage = _cursor.getDouble(_cursorIndexOfAverage);
            _item = new DailyAverage(_tmpDay,_tmpAverage);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
