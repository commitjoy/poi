/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.xssf.streaming;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.formula.eval.ErrorEval;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.Internal;
import org.apache.poi.util.LocaleUtil;
import org.apache.poi.util.NotImplemented;
import org.apache.poi.util.POILogFactory;
import org.apache.poi.util.POILogger;
import org.apache.poi.util.Removal;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

/**
 * Streaming version of XSSFRow implementing the "BigGridDemo" strategy.
*/
public class SXSSFCell implements Cell {
    private static final POILogger logger = POILogFactory.getLogger(SXSSFCell.class);

    private final SXSSFRow _row;
    private Value _value;
    private CellStyle _style;
    private Property _firstProperty;
    
    /**
     * @deprecated POI 3.15 beta 3.
     * Will be deleted when we make the CellType enum transition. See bug 59791.
     */
    @Removal(version="3.17")
    @Deprecated
    public SXSSFCell(SXSSFRow row, int cellType)
    {
        this(row, CellType.forInt((cellType)));
    }

    public SXSSFCell(SXSSFRow row,CellType cellType)
    {
        _row=row;
        setType(cellType);
    }

//start of interface implementation

    /**
     * Returns column index of this cell
     *
     * @return zero-based column index of a column in a sheet.
     */
    @Override
    public int getColumnIndex()
    {
        return _row.getCellIndex(this);
    }

    /**
     * Returns row index of a row in the sheet that contains this cell
     *
     * @return zero-based row index of a row in the sheet that contains this cell
     */
    @Override
    public int getRowIndex()
    {
        return _row.getRowNum();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CellAddress getAddress() {
        return new CellAddress(this);
    }

    /**
     * Returns the sheet this cell belongs to
     *
     * @return the sheet this cell belongs to
     */
    @Override
    public SXSSFSheet getSheet()
    {
        return _row.getSheet();
    }

    /**
     * Returns the Row this cell belongs to
     *
     * @return the Row that owns this cell
     */
    @Override
    public Row getRow()
    {
        return _row;
    }

    /**
     * Set the cells type (numeric, formula or string)
     *
     * @throws IllegalArgumentException if the specified cell type is invalid
     * @see CellType#NUMERIC
     * @see CellType#STRING
     * @see CellType#FORMULA
     * @see CellType#BLANK
     * @see CellType#BOOLEAN
     * @see CellType#ERROR
     * @deprecated POI 3.15 beta 3. Use {@link #setCellType(CellType)} instead.
     * Will be deleted when we make the CellType enum transition. See bug 59791.
     */
    @Override
    public void setCellType(int cellType)
    {
        ensureType(CellType.forInt(cellType));
    }
    /**
     * Set the cells type (numeric, formula or string)
     *
     * @throws IllegalArgumentException if the specified cell type is invalid
     */
    @Override
    public void setCellType(CellType cellType)
    {
        ensureType(cellType);
    }

    /**
     * Return the cell type.
     *
     * @return the cell type
     * @deprecated 3.15. Will return a {@link CellType} enum in the future.
     */
    @Override
    public int getCellType()
    {
        return getCellTypeEnum().getCode();
    }
    
    /**
     * Return the cell type.
     *
     * @return the cell type
     * @since POI 3.15 beta 3
     * Will be deleted when we make the CellType enum transition. See bug 59791.
     */
    @Override
    public CellType getCellTypeEnum()
    {
        return _value.getType();
    }

    /**
     * Only valid for formula cells
     * @return one of ({@link CellType#NUMERIC}, {@link CellType#STRING},
     *     {@link CellType#BOOLEAN}, {@link CellType#ERROR}) depending
     * on the cached value of the formula
     * @deprecated 3.15. Will return a {@link CellType} enum in the future.
     */
    @Override
    public int getCachedFormulaResultType()
    {
        return getCachedFormulaResultTypeEnum().getCode();
    }

    /**
     * Only valid for formula cells
     * @return one of ({@link CellType#NUMERIC}, {@link CellType#STRING},
     *     {@link CellType#BOOLEAN}, {@link CellType#ERROR}) depending
     * on the cached value of the formula
     * @since POI 3.15 beta 3
     * Will be deleted when we make the CellType enum transition. See bug 59791.
     */
    @Override
    public CellType getCachedFormulaResultTypeEnum()
    {
        if (_value.getType() != CellType.FORMULA) {
            throw new IllegalStateException("Only formula cells have cached results");
        }

        return ((FormulaValue)_value).getFormulaType();
    }

    /**
     * Set a numeric value for the cell
     *
     * @param value  the numeric value to set this cell to.  For formulas we'll set the
     *        precalculated value, for numerics we'll set its value. For other types we
     *        will change the cell to a numeric cell and set its value.
     */
    @Override
    public void setCellValue(double value)
    {
        if(Double.isInfinite(value)) {
            // Excel does not support positive/negative infinities,
            // rather, it gives a #DIV/0! error in these cases.
            setCellErrorValue(FormulaError.DIV0.getCode());
        } else if (Double.isNaN(value)){
            setCellErrorValue(FormulaError.NUM.getCode());
        } else {
            ensureTypeOrFormulaType(CellType.NUMERIC);
            if(_value.getType()==CellType.FORMULA)
                ((NumericFormulaValue)_value).setPreEvaluatedValue(value);
            else
                ((NumericValue)_value).setValue(value);
        }
    }

    /**
     * Converts the supplied date to its equivalent Excel numeric value and sets
     * that into the cell.
     * <p/>
     * <b>Note</b> - There is actually no 'DATE' cell type in Excel. In many
     * cases (when entering date values), Excel automatically adjusts the
     * <i>cell style</i> to some date format, creating the illusion that the cell
     * data type is now something besides {@link CellType#NUMERIC}.  POI
     * does not attempt to replicate this behaviour.  To make a numeric cell
     * display as a date, use {@link #setCellStyle(CellStyle)} etc.
     *
     * @param value the numeric value to set this cell to.  For formulas we'll set the
     *        precalculated value, for numerics we'll set its value. For other types we
     *        will change the cell to a numerics cell and set its value.
     */
    @Override
    public void setCellValue(Date value) {
        if(value == null) {
            setCellType(CellType.BLANK);
            return;
        }

        boolean date1904 = getSheet().getWorkbook().isDate1904();
        setCellValue(DateUtil.getExcelDate(value, date1904));
    }

    /**
     * Set a date value for the cell. Excel treats dates as numeric so you will need to format the cell as
     * a date.
     * <p>
     * This will set the cell value based on the Calendar's timezone. As Excel
     * does not support timezones this means that both 20:00+03:00 and
     * 20:00-03:00 will be reported as the same value (20:00) even that there
     * are 6 hours difference between the two times. This difference can be
     * preserved by using <code>setCellValue(value.getTime())</code> which will
     * automatically shift the times to the default timezone.
     * </p>
     *
     * @param value  the date value to set this cell to.  For formulas we'll set the
     *        precalculated value, for numerics we'll set its value. For othertypes we
     *        will change the cell to a numeric cell and set its value.
     */
    @Override
    public void setCellValue(Calendar value) {
        if(value == null) {
            setCellType(CellType.BLANK);
            return;
        }

        boolean date1904 = getSheet().getWorkbook().isDate1904();
        setCellValue( DateUtil.getExcelDate(value, date1904 ));
    }

    /**
     * Set a rich string value for the cell.
     *
     * @param value  value to set the cell to.  For formulas we'll set the formula
     * string, for String cells we'll set its value.  For other types we will
     * change the cell to a string cell and set its value.
     * If value is null then we will change the cell to a Blank cell.
     */
    @Override
    public void setCellValue(RichTextString value)
    {
        XSSFRichTextString xvalue = (XSSFRichTextString)value;
        
        if (xvalue != null && xvalue.getString() != null) {
            ensureRichTextStringType();
            
            if (xvalue.length() > SpreadsheetVersion.EXCEL2007.getMaxTextLength()) {
                throw new IllegalArgumentException("The maximum length of cell contents (text) is 32,767 characters");
            }
            if (xvalue.hasFormatting())
                logger.log(POILogger.WARN, "SXSSF doesn't support Shared Strings, rich text formatting information has be lost");
            
            ((RichTextValue)_value).setValue(xvalue);
        } else {
            setCellType(CellType.BLANK);
        }
    }

    /**
     * Set a string value for the cell.
     *
     * @param value  value to set the cell to.  For formulas we'll set the formula
     * string, for String cells we'll set its value.  For other types we will
     * change the cell to a string cell and set its value.
     * If value is null then we will change the cell to a Blank cell.
     */
    @Override
    public void setCellValue(String value)
    {
        if (value != null) {
            ensureTypeOrFormulaType(CellType.STRING);
            
            if (value.length() > SpreadsheetVersion.EXCEL2007.getMaxTextLength()) {
                throw new IllegalArgumentException("The maximum length of cell contents (text) is 32,767 characters");
            }
    
            if(_value.getType()==CellType.FORMULA)
                if(_value instanceof NumericFormulaValue) {
                    ((NumericFormulaValue) _value).setPreEvaluatedValue(Double.parseDouble(value));
                } else {
                    ((StringFormulaValue) _value).setPreEvaluatedValue(value);
                }
            else
                ((PlainStringValue)_value).setValue(value);
        } else {
            setCellType(CellType.BLANK);
        }
    }

    /**
     * Sets formula for this cell.
     * <p>
     * Note, this method only sets the formula string and does not calculate the formula value.
     * To set the precalculated value use {@link #setCellValue(double)} or {@link #setCellValue(String)}
     * </p>
     *
     * @param formula the formula to set, e.g. <code>"SUM(C4:E4)"</code>.
     *  If the argument is <code>null</code> then the current formula is removed.
     * @throws FormulaParseException if the formula has incorrect syntax or is otherwise invalid
     */
    @Override
    public void setCellFormula(String formula) throws FormulaParseException
    {
        if(formula == null) {
            setType(CellType.BLANK);
            return;
        }

        ensureFormulaType(computeTypeFromFormula(formula));
        ((FormulaValue)_value).setValue(formula);
    }
    /**
     * Return a formula for the cell, for example, <code>SUM(C4:E4)</code>
     *
     * @return a formula for the cell
     * @throws IllegalStateException if the cell type returned by {@link #getCellTypeEnum()} is not CellType.FORMULA
     */
    @Override
    public String getCellFormula()
    {
       if(_value.getType()!=CellType.FORMULA)
           throw typeMismatch(CellType.FORMULA,_value.getType(),false);
        return ((FormulaValue)_value).getValue();
    }

    /**
     * Get the value of the cell as a number.
     * <p>
     * For strings we throw an exception. For blank cells we return a 0.
     * For formulas or error cells we return the precalculated value;
     * </p>
     * @return the value of the cell as a number
     * @throws IllegalStateException if the cell type returned by {@link #getCellTypeEnum()} is CellType.STRING
     * @exception NumberFormatException if the cell value isn't a parsable <code>double</code>.
     * @see org.apache.poi.ss.usermodel.DataFormatter for turning this number into a string similar to that which Excel would render this number as.
     */
    @Override
    public double getNumericCellValue()
    {
        CellType cellType = getCellTypeEnum();
        switch(cellType) 
        {
            case BLANK:
                return 0.0;
            case FORMULA:
            {
                FormulaValue fv=(FormulaValue)_value;
                if(fv.getFormulaType()!=CellType.NUMERIC)
                      throw typeMismatch(CellType.NUMERIC, CellType.FORMULA, false);
                return ((NumericFormulaValue)_value).getPreEvaluatedValue();
            }
            case NUMERIC:
                return ((NumericValue)_value).getValue();
            default:
                throw typeMismatch(CellType.NUMERIC, cellType, false);
        }
    }

    /**
     * Get the value of the cell as a date.
     * <p>
     * For strings we throw an exception. For blank cells we return a null.
     * </p>
     * @return the value of the cell as a date
     * @throws IllegalStateException if the cell type returned by {@link #getCellTypeEnum()} is CellType.STRING
     * @exception NumberFormatException if the cell value isn't a parsable <code>double</code>.
     * @see org.apache.poi.ss.usermodel.DataFormatter for formatting  this date into a string similar to how excel does.
     */
    @Override
    public Date getDateCellValue()
    {
        CellType cellType = getCellTypeEnum();
        if (cellType == CellType.BLANK) 
        {
            return null;
        }

        double value = getNumericCellValue();
        boolean date1904 = getSheet().getWorkbook().isDate1904();
        return DateUtil.getJavaDate(value, date1904);
    }

    /**
     * Get the value of the cell as a XSSFRichTextString
     * <p>
     * For numeric cells we throw an exception. For blank cells we return an empty string.
     * For formula cells we return the pre-calculated value if a string, otherwise an exception.
     * </p>
     * @return the value of the cell as a XSSFRichTextString
     */
    @Override
    public RichTextString getRichStringCellValue()
    {
        CellType cellType = getCellTypeEnum();
        if(getCellTypeEnum() != CellType.STRING)
            throw typeMismatch(CellType.STRING, cellType, false);

        StringValue sval = (StringValue)_value;
        if(sval.isRichText())
            return ((RichTextValue)_value).getValue();
        else {
            String plainText = getStringCellValue();
            return getSheet().getWorkbook().getCreationHelper().createRichTextString(plainText);
        }
    }


    /**
     * Get the value of the cell as a string
     * <p>
     * For numeric cells we throw an exception. For blank cells we return an empty string.
     * For formulaCells that are not string Formulas, we throw an exception.
     * </p>
     * @return the value of the cell as a string
     */
    @Override
    public String getStringCellValue()
    {
        CellType cellType = getCellTypeEnum();
        switch(cellType) 
        {
            case BLANK:
                return "";
            case FORMULA:
            {
                FormulaValue fv=(FormulaValue)_value;
                if(fv.getFormulaType()!=CellType.STRING)
                      throw typeMismatch(CellType.STRING, CellType.FORMULA, false);
                return ((StringFormulaValue)_value).getPreEvaluatedValue();
            }
            case STRING:
            {
                if(((StringValue)_value).isRichText())
                    return ((RichTextValue)_value).getValue().getString();
                else
                    return ((PlainStringValue)_value).getValue();
            }
            default:
                throw typeMismatch(CellType.STRING, cellType, false);
        }
    }

    /**
     * Set a boolean value for the cell
     *
     * @param value the boolean value to set this cell to.  For formulas we'll set the
     *        precalculated value, for booleans we'll set its value. For other types we
     *        will change the cell to a boolean cell and set its value.
     */
    @Override
    public void setCellValue(boolean value)
    {
        ensureTypeOrFormulaType(CellType.BOOLEAN);
        if(_value.getType()==CellType.FORMULA)
            ((BooleanFormulaValue)_value).setPreEvaluatedValue(value);
        else
            ((BooleanValue)_value).setValue(value);
    }

    /**
     * Set a error value for the cell
     *
     * @param value the error value to set this cell to.  For formulas we'll set the
     *        precalculated value , for errors we'll set
     *        its value. For other types we will change the cell to an error
     *        cell and set its value.
     * @see org.apache.poi.ss.usermodel.FormulaError
     */
    @Override
    public void setCellErrorValue(byte value)
    {
        ensureType(CellType.ERROR);
        if(_value.getType()==CellType.FORMULA)
            ((ErrorFormulaValue)_value).setPreEvaluatedValue(value);
        else
            ((ErrorValue)_value).setValue(value);
    }

    /**
     * Get the value of the cell as a boolean.
     * <p>
     * For strings, numbers, and errors, we throw an exception. For blank cells we return a false.
     * </p>
     * @return the value of the cell as a boolean
     * @throws IllegalStateException if the cell type returned by {@link #getCellTypeEnum()}
     *   is not CellType.BOOLEAN, CellType.BLANK or CellType.FORMULA
     */
    @Override
    public boolean getBooleanCellValue()
    {
        CellType cellType = getCellTypeEnum();
        switch(cellType) 
        {
            case BLANK:
                return false;
            case FORMULA:
            {
                FormulaValue fv=(FormulaValue)_value;
                if(fv.getFormulaType()!=CellType.BOOLEAN)
                      throw typeMismatch(CellType.BOOLEAN, CellType.FORMULA, false);
                return ((BooleanFormulaValue)_value).getPreEvaluatedValue();
            }
            case BOOLEAN:
            {
                return ((BooleanValue)_value).getValue();
            }
            default:
                throw typeMismatch(CellType.BOOLEAN, cellType, false);
        }
    }

    /**
     * Get the value of the cell as an error code.
     * <p>
     * For strings, numbers, and booleans, we throw an exception.
     * For blank cells we return a 0.
     * </p>
     *
     * @return the value of the cell as an error code
     * @throws IllegalStateException if the cell type returned by {@link #getCellTypeEnum()} isn't CellType.ERROR
     * @see org.apache.poi.ss.usermodel.FormulaError for error codes
     */
    @Override
    public byte getErrorCellValue()
    {
        CellType cellType = getCellTypeEnum();
        switch(cellType) 
        {
            case BLANK:
                return 0;
            case FORMULA:
            {
                FormulaValue fv=(FormulaValue)_value;
                if(fv.getFormulaType()!=CellType.ERROR)
                      throw typeMismatch(CellType.ERROR, CellType.FORMULA, false);
                return ((ErrorFormulaValue)_value).getPreEvaluatedValue();
            }
            case ERROR:
            {
                return ((ErrorValue)_value).getValue();
            }
            default:
                throw typeMismatch(CellType.ERROR, cellType, false);
        }
    }

    /**
     * <p>Set the style for the cell.  The style should be an CellStyle created/retreived from
     * the Workbook.</p>
     * 
     * <p>To change the style of a cell without affecting other cells that use the same style,
     * use {@link org.apache.poi.ss.util.CellUtil#setCellStyleProperties(Cell, Map)}</p>
     * 
     * @param style  reference contained in the workbook.
     * If the value is null then the style information is removed causing the cell to used the default workbook style.
     * @see org.apache.poi.ss.usermodel.Workbook#createCellStyle
     */
    @Override
    public void setCellStyle(CellStyle style)
    {
        _style=style;
    }

    /**
     * Return the cell's style.
     *
     * @return the cell's style. Always not-null. Default cell style has zero index and can be obtained as
     * <code>workbook.getCellStyleAt(0)</code>
     * @see org.apache.poi.ss.usermodel.Workbook#getCellStyleAt(int)
     */
    @Override
    public CellStyle getCellStyle()
    {
        if(_style == null){
            SXSSFWorkbook wb = (SXSSFWorkbook)getRow().getSheet().getWorkbook();
            return wb.getCellStyleAt((short)0);
        } else {
            return _style;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAsActiveCell()
    {
        getSheet().setActiveCell(getAddress());
    }

    /**
     * Assign a comment to this cell
     *
     * @param comment comment associated with this cell
     */
    @Override
    public void setCellComment(Comment comment)
    {
        setProperty(Property.COMMENT,comment);
    }

    /**
     * Returns comment associated with this cell
     *
     * @return comment associated with this cell or <code>null</code> if not found
     */
    @Override
    public Comment getCellComment()
    {
        return (Comment)getPropertyValue(Property.COMMENT);
    }

    /**
     * Removes the comment for this cell, if there is one.
     */
    @Override
    public void removeCellComment()
    {
        removeProperty(Property.COMMENT);
    }

    /**
     * @return hyperlink associated with this cell or <code>null</code> if not found
     */
    @Override
    public Hyperlink getHyperlink()
    {
        return (Hyperlink)getPropertyValue(Property.HYPERLINK);
    }

    /**
     * Assign a hyperlink to this cell. If the supplied hyperlink is null, the
     * hyperlink for this cell will be removed.
     *
     * @param link hyperlink associated with this cell
     */
    @Override
    public void setHyperlink(Hyperlink link)
    {
        if (link == null) {
            removeHyperlink();
            return;
        }

        setProperty(Property.HYPERLINK,link);

        XSSFHyperlink xssfobj = (XSSFHyperlink)link;
        // Assign to us
        CellReference ref = new CellReference(getRowIndex(), getColumnIndex());
        xssfobj.setCellReference( ref );

        // Add to the lists
        getSheet()._sh.addHyperlink(xssfobj);
    }

    /**
     * Removes the hyperlink for this cell, if there is one.
     */
    @Override
    public void removeHyperlink()
    {
        removeProperty(Property.HYPERLINK);

        getSheet()._sh.removeHyperlink(getRowIndex(), getColumnIndex());
    }

    /**
     * Only valid for array formula cells
     *
     * @return range of the array formula group that the cell belongs to.
     */
// TODO: What is this?
    @NotImplemented
    public CellRangeAddress getArrayFormulaRange()
    {
        return null;
    }

    /**
     * @return <code>true</code> if this cell is part of group of cells having a common array formula.
     */
//TODO: What is this?
    @NotImplemented
    public boolean isPartOfArrayFormulaGroup()
    {
        return false;
    }
//end of interface implementation

    /**
     * Returns a string representation of the cell
     * <p>
     * Formula cells return the formula string, rather than the formula result.
     * Dates are displayed in dd-MMM-yyyy format
     * Errors are displayed as #ERR&lt;errIdx&gt;
     * </p>
     */
    @Override
    public String toString() {
        switch (getCellTypeEnum()) {
            case BLANK:
                return "";
            case BOOLEAN:
                return getBooleanCellValue() ? "TRUE" : "FALSE";
            case ERROR:
                return ErrorEval.getText(getErrorCellValue());
            case FORMULA:
                return getCellFormula();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(this)) {
                    DateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy", LocaleUtil.getUserLocale());
                    sdf.setTimeZone(LocaleUtil.getUserTimeZone());
                    return sdf.format(getDateCellValue());
                }
                return getNumericCellValue() + "";
            case STRING:
                return getRichStringCellValue().toString();
            default:
                return "Unknown Cell Type: " + getCellTypeEnum();
        }
    }

    /*package*/ void removeProperty(int type)
    {
        Property current=_firstProperty;
        Property previous=null;
        while(current!=null&&current.getType()!=type)
        {
            previous=current;
            current=current._next;
        }
        if(current!=null)
        {
            if(previous!=null)
            {
                previous._next=current._next;
            }
            else
            {
                _firstProperty=current._next;
            }
        }
    }
    /*package*/ void setProperty(int type,Object value)
    {
        Property current=_firstProperty;
        Property previous=null;
        while(current!=null&&current.getType()!=type)
        {
            previous=current;
            current=current._next;
        }
        if(current!=null)
        {
            current.setValue(value);
        }
        else
        {
            switch(type)
            {
                case Property.COMMENT:
                {
                    current=new CommentProperty(value);
                    break;
                }
                case Property.HYPERLINK:
                {
                    current=new HyperlinkProperty(value);
                    break;
                }
                default:
                {
                    throw new IllegalArgumentException("Invalid type: " + type);
                }
            }
            if(previous!=null)
            {
                previous._next=current;
            }
            else
            {
                _firstProperty=current;
            }
        }
    }
    /*package*/ Object getPropertyValue(int type)
    {
        return getPropertyValue(type,null);
    }
    /*package*/ Object getPropertyValue(int type,String defaultValue)
    {
        Property current=_firstProperty;
        while(current!=null&&current.getType()!=type) current=current._next;
        return current==null?defaultValue:current.getValue();
    }
    /*package*/ void ensurePlainStringType()
    {
        if(_value.getType()!=CellType.STRING
           ||((StringValue)_value).isRichText())
            _value=new PlainStringValue();
    }
    /*package*/ void ensureRichTextStringType()
    {
        if(_value.getType()!=CellType.STRING
           ||!((StringValue)_value).isRichText())
            _value=new RichTextValue();
    }
    /*package*/ void ensureType(CellType type)
    {
        if(_value.getType()!=type)
            setType(type);
    }
    /*package*/ void ensureFormulaType(CellType type)
    {
        if(_value.getType()!=CellType.FORMULA
           ||((FormulaValue)_value).getFormulaType()!=type)
            setFormulaType(type);
    }
    /*
     * Sets the cell type to type if it is different
     */
    /*package*/ void ensureTypeOrFormulaType(CellType type)
    {
        if(_value.getType()==type)
        {
            if(type==CellType.STRING&&((StringValue)_value).isRichText())
                setType(CellType.STRING);
            return;
        }
        if(_value.getType()==CellType.FORMULA)
        {
            if(((FormulaValue)_value).getFormulaType()==type)
                return;
            setFormulaType(type); // once a formula, always a formula
            return;
        }
        setType(type);
    }
    /**
     * changes the cell type to the specified type, and resets the value to the default value for that type
     * If cell type is the same as specified type, this will reset the value to the default value for that type
     *
     * @param type the cell type to set
     * @throws IllegalArgumentException if type is not a recognized type
     */
    /*package*/ void setType(CellType type)
    {
        switch(type)
        {
            case NUMERIC:
            {
                _value=new NumericValue();
                break;
            }
            case STRING:
            {
                PlainStringValue sval = new PlainStringValue();
                if(_value != null){
                    // if a cell is not blank then convert the old value to string
                    String str = convertCellValueToString();
                    sval.setValue(str);
                }
                _value = sval;
                break;
            }
            case FORMULA:
            {
                _value=new NumericFormulaValue();
                break;
            }
            case BLANK:
            {
                _value=new BlankValue();
                break;
            }
            case BOOLEAN:
            {
                BooleanValue bval = new BooleanValue();
                if(_value != null){
                    // if a cell is not blank then convert the old value to string
                    boolean val = convertCellValueToBoolean();
                    bval.setValue(val);
                }
                _value = bval;
                break;
            }
            case ERROR:
            {
                _value=new ErrorValue();
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Illegal type " + type);
            }
        }
    }
    /*package*/ void setFormulaType(CellType type)
    {
        Value prevValue = _value;
        switch(type)
        {
            case NUMERIC:
            {
                _value=new NumericFormulaValue();
                break;
            }
            case STRING:
            {
                _value=new StringFormulaValue();
                break;
            }
            case BOOLEAN:
            {
                _value=new BooleanFormulaValue();
                break;
            }
            case ERROR:
            {
                _value=new ErrorFormulaValue();
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Illegal type " + type);
            }
        }

        // if we had a Formula before, we should copy over the _value of the formula
        if(prevValue instanceof FormulaValue) {
            ((FormulaValue)_value)._value = ((FormulaValue)prevValue)._value;
        }
    }

//TODO: implement this correctly
    @NotImplemented
    /*package*/ CellType computeTypeFromFormula(String formula)
    {
        return CellType.NUMERIC;
    }
//COPIED FROM https://svn.apache.org/repos/asf/poi/trunk/src/ooxml/java/org/apache/poi/xssf/usermodel/XSSFCell.java since the functions are declared private there
    /**
     * Used to help format error messages
     */
    private static RuntimeException typeMismatch(CellType expectedTypeCode, CellType actualTypeCode, boolean isFormulaCell) {
        String msg = "Cannot get a " + expectedTypeCode + " value from a " + actualTypeCode
                + " " + (isFormulaCell ? "formula " : "") + "cell";
        return new IllegalStateException(msg);
    }

    private boolean convertCellValueToBoolean() {
        CellType cellType = getCellTypeEnum();

        if (cellType == CellType.FORMULA) {
            cellType = getCachedFormulaResultTypeEnum();
        }

        switch (cellType) {
            case BOOLEAN:
                return getBooleanCellValue();
            case STRING:

                String text = getStringCellValue();
                return Boolean.parseBoolean(text);
            case NUMERIC:
                return getNumericCellValue() != 0;
            case ERROR:
            case BLANK:
                return false;
            default: throw new RuntimeException("Unexpected cell type (" + cellType + ")");
        }
        
    }
    private String convertCellValueToString() {
        CellType cellType = getCellTypeEnum();
        return convertCellValueToString(cellType);
    }
    private String convertCellValueToString(CellType cellType) {
        switch (cellType) {
            case BLANK:
                return "";
            case BOOLEAN:
                return getBooleanCellValue() ? "TRUE" : "FALSE";
            case STRING:
                return getStringCellValue();
            case NUMERIC:
                return Double.toString( getNumericCellValue() );
            case ERROR:
                byte errVal = getErrorCellValue();
                return FormulaError.forInt(errVal).getString();
            case FORMULA:
                if (_value != null) {
                    FormulaValue fv = (FormulaValue)_value;
                    if (fv.getFormulaType() != CellType.FORMULA) {
                        return convertCellValueToString(fv.getFormulaType());
                    }
                }
                return "";
            default:
                throw new IllegalStateException("Unexpected cell type (" + cellType + ")");
        }
    }

//END OF COPIED CODE

    static abstract class Property
    {
        final static int COMMENT=1;
        final static int HYPERLINK=2;
        Object _value;
        Property _next;
        public Property(Object value)
        {
            _value=value;
        }
        abstract int getType();
        void setValue(Object value)
        {
            _value=value;
        }
        Object getValue()
        {
            return _value;
        }
    }
    static class CommentProperty extends Property
    {
        public CommentProperty(Object value)
        {
            super(value);
        }
        @Override
        public int getType()
        {
            return COMMENT;
        }
    }
    static class HyperlinkProperty extends Property
    {
        public HyperlinkProperty(Object value)
        {
            super(value);
        }
        @Override
        public int getType()
        {
            return HYPERLINK;
        }
    }
    interface Value
    {
        CellType getType();
    }
    static class NumericValue implements Value
    {
        double _value;
        public CellType getType()
        {
            return CellType.NUMERIC;
        }
        void setValue(double value)
        {
            _value=value;
        }
        double getValue()
        {
            return _value;
        }
    }
    static abstract class StringValue implements Value
    {
        public CellType getType()
        {
            return CellType.STRING;
        }
//We cannot introduce a new type CellType.RICH_TEXT because the types are public so we have to make rich text as a type of string
        abstract boolean isRichText(); // using the POI style which seems to avoid "instanceof".
    }
    static class PlainStringValue extends StringValue
    {
        String _value;
        void setValue(String value)
        {
            _value=value;
        }
        String getValue()
        {
            return _value;
        }
        @Override
        boolean isRichText()
        {
            return false;
        }
    }
    static class RichTextValue extends StringValue
    {
        RichTextString _value;
        @Override
        public CellType getType()
        {
            return CellType.STRING;
        }
        void setValue(RichTextString value)
        {
            _value=value;
        }
        RichTextString getValue()
        {
            return _value;
        }
        @Override
        boolean isRichText()
        {
            return true;
        }
    }
    static abstract class FormulaValue implements Value
    {
        String _value;
        public CellType getType()
        {
            return CellType.FORMULA;
        }
        void setValue(String value)
        {
            _value=value;
        }
        String getValue()
        {
            return _value;
        }
        abstract CellType getFormulaType();
    }
    static class NumericFormulaValue extends FormulaValue
    {
        double _preEvaluatedValue;
        @Override
        CellType getFormulaType()
        {
            return CellType.NUMERIC;
        }
        void setPreEvaluatedValue(double value)
        {
            _preEvaluatedValue=value;
        }
        double getPreEvaluatedValue()
        {
            return _preEvaluatedValue;
        }
    }
    static class StringFormulaValue extends FormulaValue
    {
        String _preEvaluatedValue;
        @Override
        CellType getFormulaType()
        {
            return CellType.STRING;
        }
        void setPreEvaluatedValue(String value)
        {
            _preEvaluatedValue=value;
        }
        String getPreEvaluatedValue()
        {
            return _preEvaluatedValue;
        }
    }
    static class BooleanFormulaValue extends FormulaValue
    {
        boolean _preEvaluatedValue;
        @Override
        CellType getFormulaType()
        {
            return CellType.BOOLEAN;
        }
        void setPreEvaluatedValue(boolean value)
        {
            _preEvaluatedValue=value;
        }
        boolean getPreEvaluatedValue()
        {
            return _preEvaluatedValue;
        }
    }
    static class ErrorFormulaValue extends FormulaValue
    {
        byte _preEvaluatedValue;
        @Override
        CellType getFormulaType()
        {
            return CellType.ERROR;
        }
        void setPreEvaluatedValue(byte value)
        {
            _preEvaluatedValue=value;
        }
        byte getPreEvaluatedValue()
        {
            return _preEvaluatedValue;
        }
    }
    static class BlankValue implements Value
    {
        public CellType getType()
        {
            return CellType.BLANK;
        }
    }
    static class BooleanValue implements Value
    {
        boolean _value;
        public CellType getType()
        {
            return CellType.BOOLEAN;
        }
        void setValue(boolean value)
        {
            _value=value;
        }
        boolean getValue()
        {
            return _value;
        }
    }
    static class ErrorValue implements Value
    {
        byte _value;
        public CellType getType()
        {
            return CellType.ERROR;
        }
        void setValue(byte value)
        {
            _value=value;
        }
        byte getValue()
        {
            return _value;
        }
    }
}
